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

        @Schema(description = "Id of the notification", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(
                description = "Id of the order event the notification was sent for",
                example = "0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID eventId,

        @Schema(
                description = "Id of the order the notification is about",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long orderId,

        @Schema(
                description = "Id of the customer the notification was sent to",
                example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long customerId,

        @Schema(
                description = "What happened to the order",
                example = "ORDER_CREATED",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        NotificationType type,

        @Schema(
                description = "Where the notification was delivered",
                example = "LOG",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        NotificationChannel channel,

        @Schema(
                description = "Moment the notification was stored",
                example = "2026-11-14T10:15:30Z",
                requiredMode = Schema.RequiredMode.REQUIRED
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
