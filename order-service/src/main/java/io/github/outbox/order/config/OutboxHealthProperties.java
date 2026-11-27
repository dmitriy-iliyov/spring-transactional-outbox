package io.github.outbox.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * When the outbox backlog is considered a problem.
 *
 * @param maxPendingAge   how long the oldest waiting event may wait before the service reports OUT_OF_SERVICE
 * @param maxInProcessAge how long an event may stay IN_PROCESS before the service reports DOWN; keep it below
 *                        {@code oncebox.outbox.stuck-recovery.max-batch-processing-time}, so a hanging send shows
 *                        before recovery touches the event
 */
@ConfigurationProperties(prefix = "app.outbox.health")
public record OutboxHealthProperties(Duration maxPendingAge, Duration maxInProcessAge) {

    // Both limits are required and positive: a zero limit would report a problem on the first event, a missing one
    // would silently disable the check.
    public OutboxHealthProperties {
        requirePositive(maxPendingAge, "app.outbox.health.max-pending-age");
        requirePositive(maxInProcessAge, "app.outbox.health.max-in-process-age");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be a positive duration, got " + value);
        }
    }
}
