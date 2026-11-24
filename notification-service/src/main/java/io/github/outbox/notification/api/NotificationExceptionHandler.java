package io.github.outbox.notification.api;

import io.github.outbox.notification.api.NotificationProblems.FieldViolation;
import io.github.outbox.notification.notification.NotificationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.util.List;

/**
 * Answers every failure of the service as a problem detail (RFC 9457).
 * <p>
 * Not scoped to the controllers' package: a request no handler matches (an unknown path, an unsupported method or
 * content type) fails before a controller is chosen, and a scoped advice would leave it to the default error page.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class NotificationExceptionHandler extends ResponseEntityExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotificationNotFoundException(NotificationNotFoundException exception,
                                                                             HttpServletRequest request) {
        log.debug("Request {} answered 404: {}", request.getRequestURI(), exception.getMessage());
        ProblemDetail problem = NotificationProblems.notificationNotFound(exception.getMessage(),
                request.getRequestURI(), clock.instant());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception exception, HttpServletRequest request) {
        log.error("Request {} failed", request.getRequestURI(), exception);
        ProblemDetail problem = NotificationProblems.internalError(request.getRequestURI(), clock.instant());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationFailed(exception, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldViolation> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                error instanceof FieldError fieldError
                                        ? fieldError.getField()
                                        : requestName(result.getMethodParameter()),
                                error.getDefaultMessage()
                        )))
                .toList();
        return validationFailed(exception, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
        if (statusCode.is5xxServerError()) {
            log.error("Request {} failed", uri, exception);
        } else {
            log.debug("Request {} answered {}: {}", uri, statusCode.value(), exception.getClass().getSimpleName());
        }
        // Spring builds the body of most standard exceptions inside super, so it is stamped on the way out.
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            NotificationProblems.stamp(problem, uri, clock.instant());
        }
        return response;
    }

    // The name the client used: the header, path variable or query parameter, not the Java parameter.
    private static String requestName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) {
            return header.value();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.value().isEmpty()) {
            return pathVariable.value();
        }
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        return parameter.getParameterName();
    }

    private ResponseEntity<Object> validationFailed(Exception exception, List<FieldViolation> errors,
                                                    HttpHeaders headers, WebRequest request) {
        String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
        ProblemDetail problem = NotificationProblems.validationFailed(errors, uri, clock.instant());
        return handleExceptionInternal(exception, problem, headers, HttpStatusCode.valueOf(problem.getStatus()),
                request);
    }
}
