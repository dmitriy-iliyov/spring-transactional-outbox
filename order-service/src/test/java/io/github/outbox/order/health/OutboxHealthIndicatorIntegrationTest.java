package io.github.outbox.order.health;

import io.github.outbox.order.config.OutboxHealthProperties;
import io.github.outbox.order.support.OrderSchema;
import io.github.outbox.order.support.RequiresDocker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Clock;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Testcontainers
@RequiresDocker
@SpringBootTest(classes = OutboxHealthIndicatorIntegrationTest.Application.class,
        properties = "spring.config.name=outbox-health-test")
@AutoConfigureMockMvc
class OutboxHealthIndicatorIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // The table oncebox creates, with the columns the health query reads.
    private static final String OUTBOX_EVENTS_DDL = """
            CREATE TABLE IF NOT EXISTS outbox_events (
                id UUID PRIMARY KEY,
                status VARCHAR(50) NOT NULL,
                event_type VARCHAR(255) NOT NULL,
                payload_type VARCHAR(255) NOT NULL,
                payload TEXT NOT NULL,
                retry_count INTEGER NOT NULL,
                next_retry_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
                created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
                lock_token UUID
            )
            """;

    // Only the beans the health endpoint needs, so the test does not start Kafka or the pollers.
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(OutboxHealthProperties.class)
    static class Application {

        @Bean
        DataSource dataSource() {
            return OrderSchema.dataSource(POSTGRES);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        OutboxHealthIndicator outbox(JdbcClient jdbc, Clock clock, OutboxHealthProperties properties) {
            return new OutboxHealthIndicator(new OutboxBacklogReader(jdbc, clock), properties);
        }
    }

    @Autowired
    private MockMvc mvc;

    private JdbcClient jdbc;

    @BeforeAll
    static void createOutboxTable() {
        JdbcClient.create(OrderSchema.dataSource(POSTGRES)).sql(OUTBOX_EVENTS_DDL).update();
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(OrderSchema.dataSource(POSTGRES));
        jdbc.sql("TRUNCATE outbox_events").update();
    }

    @Test
    @DisplayName("IT /actuator/health/outbox when nothing waits should be up with zero counts")
    void outbox_whenTableIsEmpty_shouldBeUpWithZeroCounts() throws Exception {
        // when
        ResultActions health = readOutboxHealth();

        // then
        health.andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details.pendingCount").value(0))
                .andExpect(jsonPath("$.details.staleInProcessCount").value(0));
    }

    @Test
    @DisplayName("IT /actuator/health/outbox when fresh events wait should be up and count them")
    void outbox_whenFreshEventsWait_shouldBeUpAndCountThem() throws Exception {
        // given
        insertEvent("PENDING", 0);
        insertEvent("PENDING", 0);
        insertEvent("IN_PROCESS", 0);
        insertEvent("PROCESSED", 60);

        // when
        ResultActions health = readOutboxHealth();

        // then
        health.andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details.pendingCount").value(2))
                .andExpect(jsonPath("$.details.oldestPendingAge").value(startsWith("PT0")));
    }

    @Test
    @DisplayName("IT /actuator/health/outbox when the oldest pending event is older than the limit should be 503")
    void outbox_whenOldestPendingEventIsTooOld_shouldBeOutOfService() throws Exception {
        // given
        insertEvent("PENDING", 6 * 60);

        // when
        ResultActions health = readOutboxHealth();

        // then
        health.andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
                .andExpect(jsonPath("$.details.pendingCount").value(1));
    }

    @Test
    @DisplayName("IT /actuator/health/outbox when an event stays in process beyond the limit should be 503 and down")
    void outbox_whenAnEventStaysInProcessTooLong_shouldBeDown() throws Exception {
        // given
        insertEvent("IN_PROCESS", 4 * 60);

        // when
        ResultActions health = readOutboxHealth();

        // then
        health.andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.details.staleInProcessCount").value(1));
    }

    private ResultActions readOutboxHealth() throws Exception {
        return mvc.perform(get("/actuator/health/outbox"));
    }

    private void insertEvent(String status, int ageInSeconds) {
        jdbc.sql("""
                        INSERT INTO outbox_events (id, status, event_type, payload_type, payload, retry_count, updated_at)
                        VALUES (gen_random_uuid(), :status, 'order-created', 'application/json', '{}', 0,
                                now() - make_interval(secs => :age))
                        """)
                .param("status", status)
                .param("age", ageInSeconds)
                .update();
    }
}
