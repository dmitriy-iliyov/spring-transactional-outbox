package io.github.outbox.notification.consumer;

import io.github.dmitriyiliyov.oncebox.core.OnceboxHeadersUtils;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;
import io.github.dmitriyiliyov.oncebox.starter.inbox.TransactionalConsumer;
import io.github.outbox.contract.OrderEvent;
import io.github.outbox.notification.notification.NotificationService;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelTraceContext;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Turns each order event into exactly one notification, however many times the event is delivered.
 * <p>
 * The inbox records the event id in the same transaction as the notification: a redelivered event finds its id
 * and is skipped, and a notification that fails rolls the id back with it, so the redelivery runs it again. The
 * payload class is resolved from the event type header by the inbox container factory.
 * <p>
 * Handling runs in an {@value #HANDLE_SPAN_NAME} span, a child of the listener's span. When the event carries a
 * valid W3C {@code traceparent}, the handling span also links to the span context of the request that caused the
 * event, so the consumer's work shows up in that request's trace; an event without a valid {@code traceparent}
 * is handled in a plain child span, exactly as before, and never fails because of the header.
 */
@Component
@RequiredArgsConstructor
public class OrderEventsListener {

    static final String HANDLE_SPAN_NAME = "order-event.handle";

    @TransactionalConsumer
    private final IdempotentConsumer idempotentConsumer;
    private final NotificationService notificationService;
    private final Tracer tracer;

    @KafkaListener(
            topics = "${app.kafka.order-events-topic}",
            containerFactory = "inboxKafkaListenerContainerFactory"
    )
    public void onOrderEvent(Message<OrderEvent> message) {
        try (ConsumerLogContext ignored = ConsumerLogContext.of(message)) {
            Span span = handleSpan(message).start();
            try (Tracer.SpanInScope ignoredSpan = tracer.withSpan(span)) {
                idempotentConsumer.consume(
                        message,
                        OnceboxHeadersUtils::extractId,
                        consumed -> notificationService.notify(OnceboxHeadersUtils.extractId(consumed),
                                consumed.getPayload())
                );
            } finally {
                span.end();
            }
        }
    }

    private Span.Builder handleSpan(Message<OrderEvent> message) {
        Span.Builder builder = tracer.spanBuilder().name(HANDLE_SPAN_NAME);
        OrderEvent event = message.getPayload();
        TraceParent traceParent = TraceParent.parse(event == null ? null : event.traceParent());
        if (!traceParent.isEmpty()) {
            builder.addLink(linkTo(traceParent));
        }
        return builder;
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
