package io.github.outbox.notification.consumer;

import io.github.dmitriyiliyov.oncebox.core.OnceboxHeadersUtils;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;
import io.github.dmitriyiliyov.oncebox.starter.inbox.TransactionalConsumer;
import io.github.outbox.contract.OrderEvent;
import io.github.outbox.notification.notification.NotificationService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns a polled batch of order events into notifications, checking the inbox once for the whole batch and
 * handling the events that were not consumed yet in one transaction. Active while
 * {@code spring.kafka.listener.type} is {@code batch} in place of the record {@link OrderEventsListener}; only one
 * of the two is registered, so an event is recorded by one consumer only.
 * <p>
 * A batch that fails is rolled back as a whole, notifications and inbox ids together. The events are then handled
 * one by one through the record {@code consume}, each in a transaction of its own, and the index of the event that
 * still fails is reported with {@link BatchListenerFailedException}. Everything before it is stored by then, and
 * the error handler retries from that event and sends it to the dead letter topic, exactly as in record mode;
 * reporting the index straight away would have had the handler commit the offsets of events that were never
 * stored. A transient failure still waits with the growing back-off of the skip policies.
 * <p>
 * Each event keeps its own log context and its own {@value OrderEventSpans#HANDLE_SPAN_NAME} span: the MDC keys
 * and the span are set around the event, not around the batch, so a log line never names the wrong event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.listener.type", havingValue = "batch")
public class OrderEventsBatchListener {

    static final String HANDLE_SPAN_NAME = OrderEventSpans.HANDLE_SPAN_NAME;

    @TransactionalConsumer
    private final IdempotentConsumer idempotentConsumer;
    private final NotificationService notificationService;
    private final Tracer tracer;

    @KafkaListener(
            topics = "${app.kafka.order-events-topic}",
            containerFactory = "inboxKafkaListenerContainerFactory"
    )
    public void onOrderEvents(List<Message<OrderEvent>> messages) {
        try {
            idempotentConsumer.consume(messages, OnceboxHeadersUtils::extractId, this::notifyAll);
        } catch (RuntimeException batchFailure) {
            // the batch is one transaction: one event that fails leaves none of them stored, so each is handled
            // again on its own, and only the event that still fails is reported to the error handler
            log.warn("Batch of {} order events failed, handling them one by one", messages.size(), batchFailure);
            notifyOneByOne(messages);
        }
    }

    private void notifyAll(List<Message<OrderEvent>> notConsumed) {
        notConsumed.forEach(this::notify);
    }

    private void notifyOneByOne(List<Message<OrderEvent>> messages) {
        for (int index = 0; index < messages.size(); index++) {
            Message<OrderEvent> message = messages.get(index);
            try {
                idempotentConsumer.consume(message, OnceboxHeadersUtils::extractId, this::notify);
            } catch (RuntimeException failure) {
                throw new BatchListenerFailedException("Order event at index " + index + " failed", failure, index);
            }
        }
    }

    private void notify(Message<OrderEvent> message) {
        try (ConsumerLogContext ignored = ConsumerLogContext.of(message)) {
            Span span = OrderEventSpans.start(tracer, message);
            try (Tracer.SpanInScope ignoredSpan = tracer.withSpan(span)) {
                notificationService.notify(OnceboxHeadersUtils.extractId(message), message.getPayload());
            } finally {
                span.end();
            }
        }
    }
}
