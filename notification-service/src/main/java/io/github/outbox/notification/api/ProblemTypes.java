package io.github.outbox.notification.api;

import java.net.URI;

/**
 * The {@code type} of every problem the notification API answers with. A client branches on these, so they are
 * public contract: a type is never renamed, and one type always has the same status and title.
 */
public final class ProblemTypes {

    private static final String BASE = "https://spring-transactional-outbox/errors/";

    public static final URI VALIDATION_FAILED = URI.create(BASE + "validation-failed");
    public static final URI NOTIFICATION_NOT_FOUND = URI.create(BASE + "notification-not-found");
    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    private ProblemTypes() {
    }
}
