package io.github.outbox.notification.consumer;

import io.github.dmitriyiliyov.oncebox.core.OnceboxHeadersUtils;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;
import io.github.dmitriyiliyov.oncebox.starter.inbox.TransactionalConsumer;
import io.github.outbox.contract.OrderEvent;
import io.github.outbox.notification.notification.NotificationService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * One event, one inbox check and one transaction. Active while {@code spring.kafka.listener.type} is {@code single},
 * the default; with {@code batch} the {@link OrderEventsBatchListener} takes the same topic and checks the inbox
 * once per poll instead. Only one of the two is registered, so an event is recorded by one consumer only.
 * <p>
 * Handling runs in an {@value OrderEventSpans#HANDLE_SPAN_NAME} span, a child of the listener's span. When the event
 * carries a valid W3C {@code traceparent}, the handling span also links to the span context of the request that
 * caused the event, so the consumer's work shows up in that request's trace; see {@link OrderEventSpans}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.listener.type", havingValue = "single", matchIfMissing = true)
public class OrderEventsListener {

    static final String HANDLE_SPAN_NAME = OrderEventSpans.HANDLE_SPAN_NAME;

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
            Span span = OrderEventSpans.start(tracer, message);
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
}
