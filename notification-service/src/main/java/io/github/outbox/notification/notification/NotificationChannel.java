package io.github.outbox.notification.notification;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Where a notification is delivered. {@link #LOG} writes it to the service log.
 */
@Schema(description = "Where a notification is delivered: LOG - written to the service log", example = "LOG",
        enumAsRef = true)
public enum NotificationChannel {
    LOG
}
