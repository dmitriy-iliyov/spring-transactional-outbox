package io.github.outbox.notification.consumer;

import io.github.outbox.contract.OrderEvent;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelTraceContext;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.springframework.messaging.Message;

/**
 * Starts the span each event is handled in, whichever listener delivered it.
 * <p>
 * The span is a child of the listener's span. When the event carries a valid W3C {@code traceparent}, it also links
 * to the span context of the request that caused the event, so the consumer's work shows up in that request's
 * trace; an event without a valid {@code traceparent} is handled in a plain child span and never fails because of
 * the header.
 */
final class OrderEventSpans {

    static final String HANDLE_SPAN_NAME = "order-event.handle";

    private OrderEventSpans() {
    }

    /**
     * @return the span of one event, started by the caller so that the call site decides how to close it
     */
    static Span start(Tracer tracer, Message<OrderEvent> message) {
        Span.Builder builder = tracer.spanBuilder().name(HANDLE_SPAN_NAME);
        OrderEvent event = message.getPayload();
        TraceParent traceParent = TraceParent.parse(event == null ? null : event.traceParent());
        if (!traceParent.isEmpty()) {
            builder.addLink(linkTo(traceParent));
        }
        return builder.start();
    }

    // The causing request lives in another service: a remote parent, referenced by a link, not by parenthood.
    private static Link linkTo(TraceParent traceParent) {
        SpanContext spanContext = SpanContext.createFromRemoteParent(
                traceParent.traceId(),
                traceParent.spanId(),
                traceParent.sampled() ? TraceFlags.getSampled() : TraceFlags.getDefault(),
                TraceState.getDefault());
        return new Link(OtelTraceContext.fromOtel(spanContext));
    }
}
