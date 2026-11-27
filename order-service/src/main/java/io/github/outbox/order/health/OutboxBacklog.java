package io.github.outbox.order.health;

import java.time.Duration;

/**
 * What the outbox table looks like at the moment of a health check.
 *
 * @param pendingCount         how many events wait to be published
 * @param oldestPendingAge     how long the oldest of them has been waiting; zero when nothing waits
 * @param staleInProcessCount  how many events have stayed {@code IN_PROCESS} longer than the allowed time
 */
public record OutboxBacklog(long pendingCount, Duration oldestPendingAge, long staleInProcessCount) {
}
