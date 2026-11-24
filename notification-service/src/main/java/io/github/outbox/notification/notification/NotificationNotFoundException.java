package io.github.outbox.notification.notification;

/**
 * Thrown when no notification exists for the requested id.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(long notificationId) {
        super("Notification %d not found".formatted(notificationId));
    }
}
