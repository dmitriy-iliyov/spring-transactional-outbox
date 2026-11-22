package io.github.outbox.order.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * Gives every request a correlation id and puts it in the MDC as {@value #MDC_KEY} for the whole request, so that
 * every log line of it carries the id. The client's {@code X-Correlation-Id} is kept; a request without one gets a
 * new id, set as that header for everything downstream, so the controller, the stored events and the log agree. The
 * id the request runs under is also set as the {@code X-Correlation-Id} response header before the rest of the
 * chain runs, so it is on every answer - errors included - and the caller can find its request in the logs. A
 * header longer than the API accepts is left to validation: it is neither logged nor echoed. The MDC is cleared
 * when the request ends, keys put by the service included, so nothing leaks into the next request served by the
 * same thread.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";

    static final int MAX_LENGTH = 128;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String sent = request.getHeader(OrderController.CORRELATION_ID_HEADER);
            if (sent == null || sent.isBlank()) {
                String generated = UUID.randomUUID().toString();
                MDC.put(MDC_KEY, generated);
                response.setHeader(OrderController.CORRELATION_ID_HEADER, generated);
                chain.doFilter(new WithCorrelationIdHeader(request, generated), response);
            } else {
                if (sent.length() <= MAX_LENGTH) {
                    MDC.put(MDC_KEY, sent);
                    response.setHeader(OrderController.CORRELATION_ID_HEADER, sent);
                }
                chain.doFilter(request, response);
            }
        } finally {
            // the whole MDC of the request, including the keys the service put on the way
            MDC.clear();
        }
    }

    private static final class WithCorrelationIdHeader extends HttpServletRequestWrapper {

        private final String correlationId;

        WithCorrelationIdHeader(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            return isCorrelationId(name) ? correlationId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isCorrelationId(name) ? Collections.enumeration(List.of(correlationId))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.add(OrderController.CORRELATION_ID_HEADER);
            return Collections.enumeration(names);
        }

        private static boolean isCorrelationId(String name) {
            return OrderController.CORRELATION_ID_HEADER.equalsIgnoreCase(name);
        }
    }
}
