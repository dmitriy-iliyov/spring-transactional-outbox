package io.github.outbox.notification.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What happened to the order: ORDER_CREATED - it was placed, ORDER_CANCELLED - it was "
        + "cancelled", example = "ORDER_CREATED", enumAsRef = true)
public enum NotificationType {
    ORDER_CREATED,
    ORDER_CANCELLED
}
