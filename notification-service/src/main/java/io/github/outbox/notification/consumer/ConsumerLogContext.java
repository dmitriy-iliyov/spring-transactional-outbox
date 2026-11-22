package io.github.outbox.notification.consumer;

import io.github.outbox.contract.OrderEvent;
import org.slf4j.MDC;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Names the consumed event in the MDC while it is handled, so that every log line written meanwhile carries the
 * event id, its type, the order and customer it is about, and the correlation id and trace id of the request that
 * caused it. Closing removes exactly the keys it put: the listener thread may hold other keys, such as those of
 * tracing.
 * <p>
 * The trace id of the causing request goes under a key of its own: the record is handled in the consumer's own
 * trace, whose id tracing keeps under {@code traceId}.
 */
final class ConsumerLogContext implements AutoCloseable {

    static final String EVENT_ID = "eventId";
    static final String EVENT_TYPE = "eventType";
    static final String ORDER_ID = "orderId";
    static final String CUSTOMER_ID = "customerId";
    static final String CORRELATION_ID = "correlationId";
    static final String ORIGIN_TRACE_ID = "originTraceId";

    private static final String EVENT_ID_HEADER = "oncebox_event_id";
    private static final String EVENT_TYPE_HEADER = "oncebox_event_type";

    private final List<String> keys = new ArrayList<>();

    private ConsumerLogContext() {
    }

    /**
     * Puts what the message carries; a header or field that is missing, or a trace parent that is not a W3C
     * {@code traceparent}, is left out rather than logged as empty.
     */
    static ConsumerLogContext of(Message<OrderEvent> message) {
        ConsumerLogContext context = new ConsumerLogContext();
        context.put(EVENT_ID, header(message, EVENT_ID_HEADER));
        context.put(EVENT_TYPE, header(message, EVENT_TYPE_HEADER));
        OrderEvent event = message.getPayload();
        if (event != null) {
            context.put(ORDER_ID, String.valueOf(event.orderId()));
            context.put(CUSTOMER_ID, String.valueOf(event.customerId()));
            context.put(CORRELATION_ID, event.correlationId());
            context.put(ORIGIN_TRACE_ID, traceId(event.traceParent()));
        }
        return context;
    }

    @Override
    public void close() {
        keys.forEach(MDC::remove);
    }

    private void put(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
            keys.add(key);
        }
    }

    private static String traceId(String traceParent) {
        TraceParent parsed = TraceParent.parse(traceParent);
        return parsed.isEmpty() ? null : parsed.traceId();
    }

    // Kafka hands raw header values over as bytes; the inbox factory may already have turned them into strings.
    private static String header(Message<?> message, String name) {
        Object value = message.getHeaders().get(name);
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? null : value.toString();
    }
}
