package io.github.outbox.notification.e2e;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.contract.OrderLine;
import io.github.outbox.notification.e2e.NotificationApi.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventsDeliveryFlowEndToEndTest extends NotificationServiceEndToEndTestBase {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-14T10:15:30Z");

    private final JdbcClient jdbc = JdbcClient.create(new DriverManagerDataSource(
            NotificationEndToEndContainers.POSTGRES.getJdbcUrl(),
            NotificationEndToEndContainers.POSTGRES.getUsername(),
            NotificationEndToEndContainers.POSTGRES.getPassword()
    ));

    OrderEventsDeliveryFlowEndToEndTest(@LocalServerPort int port) {
        super(port);
    }

    @Test
    @DisplayName("E2E delivery when an order created event arrives should store one notification for it")
    void delivery_whenOrderCreatedEventArrives_shouldStoreOneNotificationForIt() {
        // given
        long orderId = newOrderId();
        UUID eventId = UUID.randomUUID();

        // when
        orderService.publish(eventId, OrderEventTypes.ORDER_CREATED, created(orderId));

        // then
        Waits.awaitDelivery().until(() -> notifications(orderId).json().size() == 1);
        Response response = notifications(orderId);
        assertThat(response.json().get(0).get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(response.json().get(0).get("type").asText()).isEqualTo("ORDER_CREATED");
        assertThat(response.json().get(0).get("customerId").asLong()).isEqualTo(42L);
    }

    @Test
    @DisplayName("E2E delivery when the same event is delivered three times should store one notification")
    void delivery_whenSameEventIsDeliveredThreeTimes_shouldStoreOneNotification() {
        // given
        long orderId = newOrderId();
        UUID eventId = UUID.randomUUID();

        // when
        for (int i = 0; i < 3; i++) {
            orderService.publish(eventId, OrderEventTypes.ORDER_CREATED, created(orderId));
        }

        // then
        Waits.awaitDelivery().until(() -> notifications(orderId).json().size() == 1);
        Waits.awaitQuiet().until(() -> notifications(orderId).json().size() == 1);
    }

    @Test
    @DisplayName("E2E delivery when the same payload comes under two event ids should store two notifications")
    void delivery_whenSamePayloadComesUnderTwoEventIds_shouldStoreTwoNotifications() {
        // given
        long orderId = newOrderId();

        // when
        orderService.publish(UUID.randomUUID(), OrderEventTypes.ORDER_CREATED, created(orderId));
        orderService.publish(UUID.randomUUID(), OrderEventTypes.ORDER_CREATED, created(orderId));

        // then
        Waits.awaitDelivery().until(() -> notifications(orderId).json().size() == 2);
    }

    @Test
    @DisplayName("E2E delivery when an order is created and then cancelled should store both notifications")
    void delivery_whenOrderIsCreatedAndThenCancelled_shouldStoreBothNotifications() {
        // given
        long orderId = newOrderId();

        // when
        orderService.publish(UUID.randomUUID(), OrderEventTypes.ORDER_CREATED, created(orderId));
        orderService.publish(UUID.randomUUID(), OrderEventTypes.ORDER_CANCELLED,
                new OrderCancelledEvent(orderId, 42L, OCCURRED_AT.plusSeconds(60), "c-2", null));

        // then
        Waits.awaitDelivery().until(() -> notifications(orderId).json().size() == 2);
        List<String> types = notifications(orderId).json().findValuesAsText("type");
        assertThat(types).containsExactlyInAnyOrder("ORDER_CREATED", "ORDER_CANCELLED");
    }

    @Test
    @DisplayName("E2E delivery when the inbox record is removed and the event is redelivered should store one notification")
    void delivery_whenInboxRecordIsRemovedAndEventIsRedelivered_shouldStoreOneNotification() {
        // given
        long orderId = newOrderId();
        UUID eventId = UUID.randomUUID();
        orderService.publish(eventId, OrderEventTypes.ORDER_CREATED, created(orderId));
        Waits.awaitDelivery().until(() -> notifications(orderId).json().size() == 1);
        long firstNotificationId = notifications(orderId).json().get(0).get("id").asLong();
        int deleted = jdbc.sql("DELETE FROM inbox_events WHERE id = :eventId")
                .param("eventId", eventId)
                .update();
        assertThat(deleted).isEqualTo(1);

        // when
        orderService.publish(eventId, OrderEventTypes.ORDER_CREATED, created(orderId));

        // then
        Waits.awaitDelivery().until(() -> inboxCount(eventId) == 1L);
        Waits.awaitQuiet().until(() -> notifications(orderId).json().size() == 1);
        assertThat(notifications(orderId).json().get(0).get("id").asLong()).isEqualTo(firstNotificationId);
    }

    private long inboxCount(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM inbox_events WHERE id = :eventId")
                .param("eventId", eventId)
                .query(Long.class)
                .single();
    }

    private Response notifications(long orderId) {
        Response response = api.findByOrderId(orderId);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response;
    }

    private static OrderCreatedEvent created(long orderId) {
        return new OrderCreatedEvent(orderId, 42L, List.of(new OrderLine("sku-1", 2, new BigDecimal("10.50"))),
                new BigDecimal("21.00"), OCCURRED_AT, "c-1", null);
    }
}
