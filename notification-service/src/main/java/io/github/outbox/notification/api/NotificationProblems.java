package io.github.outbox.notification.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * The one place that decides status, type and title of every problem the notification API answers with.
 */
final class NotificationProblems {

    static final String INTERNAL_ERROR_DETAIL = "The operation could not be completed";

    private NotificationProblems() {
    }

    static ProblemDetail validationFailed(List<FieldViolation> errors, String instance, Instant timestamp) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_FAILED, "Validation failed",
                "Request validation failed", instance, timestamp);
        problem.setProperty("errors", errors);
        return problem;
    }

    static ProblemDetail notificationNotFound(String detail, String instance, Instant timestamp) {
        return problem(HttpStatus.NOT_FOUND, ProblemTypes.NOTIFICATION_NOT_FOUND, "Notification not found", detail,
                instance, timestamp);
    }

    static ProblemDetail internalError(String instance, Instant timestamp) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.INTERNAL_ERROR, "Internal error",
                INTERNAL_ERROR_DETAIL, instance, timestamp);
    }

    static void stamp(ProblemDetail problem, String instance, Instant timestamp) {
        problem.setInstance(URI.create(instance));
        problem.setProperty("path", instance);
        problem.setProperty("timestamp", timestamp);
    }

    private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail, String instance,
                                         Instant timestamp) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        stamp(problem, instance, timestamp);
        return problem;
    }

    /**
     * One rejected value of a request: {@code field} is the name in the JSON body or the parameter name.
     */
    @Schema(description = "Rejected value of a request")
    record FieldViolation(

            @Schema(
                    description = "Name of the rejected value: its path in the JSON body, or the header, path "
                            + "variable or query parameter name",
                    example = "orderId",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String field,

            @Schema(
                    description = "Why the value was rejected",
                    example = "orderId must be positive",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            String message
    ) {
    }
}
