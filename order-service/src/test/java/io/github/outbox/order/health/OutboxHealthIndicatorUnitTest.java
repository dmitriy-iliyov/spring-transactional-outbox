package io.github.outbox.order.health;

import io.github.outbox.order.config.OutboxHealthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxHealthIndicatorUnitTest {

    private static final OutboxHealthProperties PROPERTIES = new OutboxHealthProperties(
            Duration.ofMinutes(5), Duration.ofMinutes(3));

    @Mock
    private OutboxBacklogReader reader;

    @Test
    @DisplayName("UT health() when nothing waits and nothing hangs should be up with zero counts")
    void health_whenOutboxIsEmpty_shouldBeUpWithZeroCounts() {
        // given
        givenBacklog(0, Duration.ZERO, 0);
        OutboxHealthIndicator tested = new OutboxHealthIndicator(reader, PROPERTIES);

        // when
        Health health = tested.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("pendingCount", 0L)
                .containsEntry("oldestPendingAge", "PT0S")
                .containsEntry("staleInProcessCount", 0L);
    }

    @Test
    @DisplayName("UT health() when the backlog is inside both limits should be up with the counts as details")
    void health_whenBacklogIsInsideTheLimits_shouldBeUpWithCounts() {
        // given
        givenBacklog(12, Duration.ofMinutes(4), 0);
        OutboxHealthIndicator tested = new OutboxHealthIndicator(reader, PROPERTIES);

        // when
        Health health = tested.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("pendingCount", 12L)
                .containsEntry("oldestPendingAge", "PT4M")
                .containsEntry("maxPendingAge", "PT5M")
                .containsEntry("maxInProcessAge", "PT3M");
    }

    @Test
    @DisplayName("UT health() when the oldest pending event waited longer than the limit should be out of service")
    void health_whenOldestPendingEventIsOlderThanTheLimit_shouldBeOutOfService() {
        // given
        givenBacklog(3, Duration.ofMinutes(6), 0);
        OutboxHealthIndicator tested = new OutboxHealthIndicator(reader, PROPERTIES);

        // when
        Health health = tested.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("pendingCount", 3L);
    }

    @Test
    @DisplayName("UT health() when an event stays in process longer than the limit should be down")
    void health_whenAnEventStaysInProcessTooLong_shouldBeDown() {
        // given
        givenBacklog(0, Duration.ZERO, 1);
        OutboxHealthIndicator tested = new OutboxHealthIndicator(reader, PROPERTIES);

        // when
        Health health = tested.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("staleInProcessCount", 1L);
    }

    @Test
    @DisplayName("UT health() when a send hangs and a backlog waits should be down, the worse of the two")
    void health_whenAnEventHangsAndABacklogWaits_shouldBeDown() {
        // given
        givenBacklog(7, Duration.ofMinutes(30), 2);
        OutboxHealthIndicator tested = new OutboxHealthIndicator(reader, PROPERTIES);

        // when
        Health health = tested.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    private void givenBacklog(long pendingCount, Duration oldestPendingAge, long staleInProcessCount) {
        when(reader.read(PROPERTIES.maxInProcessAge()))
                .thenReturn(new OutboxBacklog(pendingCount, oldestPendingAge, staleInProcessCount));
    }
}
