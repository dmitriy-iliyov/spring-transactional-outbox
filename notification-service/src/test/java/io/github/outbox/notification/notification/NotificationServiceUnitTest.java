package io.github.outbox.notification.notification;

import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderCreatedEvent;
import io.github.outbox.contract.OrderLine;
import io.github.outbox.notification.api.NotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceUnitTest {

    private static final UUID EVENT_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static final Instant NOW = Instant.parse("2026-11-14T10:15:30.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-11-14T10:15:30.123456Z");

    @Mock
    private NotificationRepository repository;

    private NotificationService tested;

    @BeforeEach
    void setUp() {
        tested = new NotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("UT notify() when an order was created should store an order-created notification on the log channel")
    void notify_whenOrderWasCreated_shouldStoreOrderCreatedNotificationOnTheLogChannel() {
        // given
        OrderCreatedEvent event = new OrderCreatedEvent(7L, 42L, List.of(new OrderLine("sku-1", 1, BigDecimal.ONE)),
                BigDecimal.ONE, NOW, "c-1", null);
        Notification stored = new Notification(1L, EVENT_ID, 7L, 42L, NotificationType.ORDER_CREATED,
                NotificationChannel.LOG, NOW_IN_MICROS);
        when(repository.save(EVENT_ID, 7L, 42L, NotificationType.ORDER_CREATED, NotificationChannel.LOG,
                NOW_IN_MICROS)).thenReturn(Optional.of(stored));

        // when
        Optional<Notification> result = tested.notify(EVENT_ID, event);

        // then
        assertThat(result).contains(stored);
        verify(repository).save(EVENT_ID, 7L, 42L, NotificationType.ORDER_CREATED, NotificationChannel.LOG,
                NOW_IN_MICROS);
    }

    @Test
    @DisplayName("UT notify() when an order was cancelled should store an order-cancelled notification")
    void notify_whenOrderWasCancelled_shouldStoreOrderCancelledNotification() {
        // given
        OrderCancelledEvent event = new OrderCancelledEvent(7L, 42L, NOW, "c-1", null);
        Notification stored = new Notification(2L, EVENT_ID, 7L, 42L, NotificationType.ORDER_CANCELLED,
                NotificationChannel.LOG, NOW_IN_MICROS);
        when(repository.save(EVENT_ID, 7L, 42L, NotificationType.ORDER_CANCELLED, NotificationChannel.LOG,
                NOW_IN_MICROS)).thenReturn(Optional.of(stored));

        // when
        Optional<Notification> result = tested.notify(EVENT_ID, event);

        // then
        assertThat(result).contains(stored);
        verify(repository).save(EVENT_ID, 7L, 42L, NotificationType.ORDER_CANCELLED, NotificationChannel.LOG,
                NOW_IN_MICROS);
    }

    @Test
    @DisplayName("UT notify() when a notification for the event was already stored should return empty without failing")
    void notify_whenNotificationWasAlreadyStored_shouldReturnEmptyWithoutFailing() {
        // given
        OrderCreatedEvent event = new OrderCreatedEvent(7L, 42L, List.of(new OrderLine("sku-1", 1, BigDecimal.ONE)),
                BigDecimal.ONE, NOW, "c-1", null);
        when(repository.save(EVENT_ID, 7L, 42L, NotificationType.ORDER_CREATED, NotificationChannel.LOG,
                NOW_IN_MICROS)).thenReturn(Optional.empty());

        // when
        Optional<Notification> result = tested.notify(EVENT_ID, event);

        // then
        assertThat(result).isEmpty();
        verify(repository).save(EVENT_ID, 7L, 42L, NotificationType.ORDER_CREATED, NotificationChannel.LOG,
                NOW_IN_MICROS);
    }

    @Test
    @DisplayName("UT findById() when the notification exists should return it as a response")
    void findById_whenNotificationExists_shouldReturnItAsAResponse() {
        // given
        Notification notification = new Notification(1L, EVENT_ID, 7L, 42L, NotificationType.ORDER_CREATED,
                NotificationChannel.LOG, NOW_IN_MICROS);
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        // when
        NotificationResponse found = tested.findById(1L);

        // then
        assertThat(found).isEqualTo(NotificationResponse.from(notification));
    }

    @Test
    @DisplayName("UT findById() when the notification does not exist should throw NotificationNotFoundException")
    void findById_whenNotificationDoesNotExist_shouldThrowNotificationNotFoundException() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> tested.findById(1L))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessage("Notification 1 not found");
    }

    @Test
    @DisplayName("UT findByOrderId() when the order has notifications should return them as responses")
    void findByOrderId_whenOrderHasNotifications_shouldReturnThemAsResponses() {
        // given
        Notification notification = new Notification(1L, EVENT_ID, 7L, 42L, NotificationType.ORDER_CREATED,
                NotificationChannel.LOG, NOW_IN_MICROS);
        when(repository.findByOrderId(7L)).thenReturn(List.of(notification));

        // when
        List<NotificationResponse> found = tested.findByOrderId(7L);

        // then
        assertThat(found).containsExactly(NotificationResponse.from(notification));
    }
}
