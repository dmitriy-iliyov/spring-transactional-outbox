package io.github.outbox.notification;

import io.github.outbox.notification.support.NotificationSchema;
import io.github.outbox.notification.support.RequiresDocker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@RequiresDocker
class NotificationSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;

    private JdbcClient jdbc;

    @BeforeAll
    static void migrate() throws Exception {
        dataSource = NotificationSchema.dataSource(POSTGRES);
        NotificationSchema.migrate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("TRUNCATE notifications RESTART IDENTITY").update();
    }

    @Test
    @DisplayName("IT migrate() when applied again should leave the schema and its rows as they were")
    void migrate_whenAppliedAgain_shouldLeaveTheSchemaAndItsRowsAsTheyWere() throws Exception {
        // given
        insert("ORDER_CREATED", "LOG");

        // when
        NotificationSchema.migrate(dataSource);

        // then
        assertThat(jdbc.sql("SELECT count(*) FROM notifications").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("IT notifications when type is unknown should reject the row")
    void notifications_whenTypeIsUnknown_shouldRejectTheRow() {
        // when / then
        assertThatThrownBy(() -> insert("ORDER_SHIPPED", "LOG")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("IT notifications when channel is unknown should reject the row")
    void notifications_whenChannelIsUnknown_shouldRejectTheRow() {
        // when / then
        assertThatThrownBy(() -> insert("ORDER_CREATED", "SMS")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("IT notifications when event_id is duplicated should reject the row")
    void notifications_whenEventIdIsDuplicated_shouldRejectTheRow() {
        // given
        UUID eventId = UUID.randomUUID();
        insert(eventId, "ORDER_CREATED", "LOG");

        // when / then
        assertThatThrownBy(() -> insert(eventId, "ORDER_CREATED", "LOG"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(String type, String channel) {
        insert(UUID.randomUUID(), type, channel);
    }

    private void insert(UUID eventId, String type, String channel) {
        jdbc.sql("""
                        INSERT INTO notifications (event_id, order_id, customer_id, type, channel, created_at)
                        VALUES (:eventId, 7, 42, :type, :channel, now())
                        """)
                .param("eventId", eventId)
                .param("type", type)
                .param("channel", channel)
                .update();
    }
}
