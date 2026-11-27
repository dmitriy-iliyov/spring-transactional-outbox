package io.github.outbox.order.health;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Reads the outbox backlog in one query, so a health check does not scan the table once per question.
 *
 * <p>The age of a pending event is measured from {@code updated_at} and not from {@code created_at}: a retry puts the
 * event back to {@code PENDING} with a later {@code next_retry_at}, and that wait is the business backoff, not a
 * stalled publisher.
 */
public class OutboxBacklogReader {

    // count of PENDING, the moment the oldest PENDING was last touched, count of IN_PROCESS older than the threshold
    private static final String BACKLOG_SQL = """
            SELECT count(*) FILTER (WHERE status = 'PENDING') AS pending_count,
                   min(updated_at) FILTER (WHERE status = 'PENDING') AS oldest_pending_since,
                   count(*) FILTER (WHERE status = 'IN_PROCESS' AND updated_at < :in_process_before) AS stale_in_process_count
            FROM outbox_events
            WHERE status IN ('PENDING', 'IN_PROCESS')
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    public OutboxBacklogReader(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public OutboxBacklog read(Duration maxInProcessAge) {
        Instant now = clock.instant();
        return jdbc.sql(BACKLOG_SQL)
                .param("in_process_before", now.minus(maxInProcessAge).atOffset(ZoneOffset.UTC))
                .query((rs, rowNum) -> new OutboxBacklog(
                        rs.getLong("pending_count"),
                        age(rs.getObject("oldest_pending_since", OffsetDateTime.class), now),
                        rs.getLong("stale_in_process_count")))
                .single();
    }

    private static Duration age(OffsetDateTime since, Instant now) {
        return since == null ? Duration.ZERO : Duration.between(since.toInstant(), now);
    }
}
