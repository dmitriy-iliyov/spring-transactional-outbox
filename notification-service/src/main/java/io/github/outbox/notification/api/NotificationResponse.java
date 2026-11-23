package io.github.outbox.notification.api;

import io.github.outbox.notification.notification.Notification;
import io.github.outbox.notification.notification.NotificationChannel;
import io.github.outbox.notification.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification as the API returns it.
 *
 * @param id         id of the notification
 * @param eventId    id of the order event it was sent for
 * @param orderId    order it is about
 * @param customerId customer it was sent to
 * @param type       what happened to the order
 * @param channel    where it was delivered
 * @param createdAt  the moment it was stored
 */
@Schema(description = "Notification sent for an order event")
public record NotificationResponse(

        @Schema(
                description = "Id of the notification",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "123"
        )
        long id,

        @Schema(
                description = "Id of the order event the notification was sent for",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID eventId,

        @Schema(
                description = "Id of the order the notification is about",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "1"
        )
        long orderId,

        @Schema(
                description = "Id of the customer the notification was sent to",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "42"
        )
        long customerId,

        @Schema(
                description = "What happened to the order",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "ORDER_CREATED"
        )
        NotificationType type,

        @Schema(
                description = "Where the notification was delivered",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "EMAIL"
        )
        NotificationChannel channel,

        @Schema(
                description = "Moment the notification was stored",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2023-01-01T12:00:00Z"
        )
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.eventId(),
                notification.orderId(),
                notification.customerId(),
                notification.type(),
                notification.channel(),
                notification.createdAt()
        );
    }
}
