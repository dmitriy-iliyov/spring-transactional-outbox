package io.github.outbox.notification.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final JdbcClient jdbc;

    public Optional<Notification> save(UUID eventId, long orderId, long customerId, NotificationType type,
                                       NotificationChannel channel, Instant createdAt) {
        return jdbc.sql("""
                        INSERT INTO notifications (event_id, order_id, customer_id, type, channel, created_at)
                        VALUES (:eventId, :orderId, :customerId, :type, :channel, :createdAt)
                        ON CONFLICT (event_id) DO NOTHING
                        RETURNING id
                        """)
                .param("eventId", eventId)
                .param("orderId", orderId)
                .param("customerId", customerId)
                .param("type", type.name())
                .param("channel", channel.name())
                .param("createdAt", Timestamp.from(createdAt))
                .query(Long.class)
                .optional()
                .map(id -> new Notification(id, eventId, orderId, customerId, type, channel, createdAt));
    }

    public List<Notification> findByOrderId(long orderId) {
        return jdbc.sql("""
                        SELECT id, event_id, order_id, customer_id, type, channel, created_at
                        FROM notifications
                        WHERE order_id = :orderId
                        ORDER BY id
                        """)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new Notification(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getLong("order_id"),
                        rs.getLong("customer_id"),
                        NotificationType.valueOf(rs.getString("type")),
                        NotificationChannel.valueOf(rs.getString("channel")),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();
    }
}
