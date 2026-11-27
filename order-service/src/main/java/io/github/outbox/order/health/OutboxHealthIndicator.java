package io.github.outbox.order.health;

import io.github.outbox.order.config.OutboxHealthProperties;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Reports whether the outbox still keeps up with the orders written to it.
 *
 * <ul>
 *   <li>{@code UP} - the backlog is inside both limits, the counts are there as details;</li>
 *   <li>{@code OUT_OF_SERVICE} - the oldest pending event has waited longer than
 *       {@code app.outbox.health.max-pending-age}: publishing falls behind, so the service should not take new
 *       traffic, even though orders are still accepted;</li>
 *   <li>{@code DOWN} - an event has stayed {@code IN_PROCESS} longer than
 *       {@code app.outbox.health.max-in-process-age}: a send hangs, and this shows before oncebox's stuck recovery
 *       moves the event.</li>
 * </ul>
 */
public class OutboxHealthIndicator extends AbstractHealthIndicator {

    private final OutboxBacklogReader reader;
    private final OutboxHealthProperties properties;

    public OutboxHealthIndicator(OutboxBacklogReader reader, OutboxHealthProperties properties) {
        super("Outbox health check failed");
        this.reader = reader;
        this.properties = properties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        OutboxBacklog backlog = reader.read(properties.maxInProcessAge());
        builder.withDetail("pendingCount", backlog.pendingCount())
                .withDetail("oldestPendingAge", backlog.oldestPendingAge().toString())
                .withDetail("staleInProcessCount", backlog.staleInProcessCount())
                .withDetail("maxPendingAge", properties.maxPendingAge().toString())
                .withDetail("maxInProcessAge", properties.maxInProcessAge().toString());
        if (backlog.staleInProcessCount() > 0) {
            // a send that hangs is worse than a backlog: events are neither published nor retried
            builder.down();
            return;
        }
        if (backlog.oldestPendingAge().compareTo(properties.maxPendingAge()) > 0) {
            builder.status(Status.OUT_OF_SERVICE);
            return;
        }
        builder.up();
    }
}
