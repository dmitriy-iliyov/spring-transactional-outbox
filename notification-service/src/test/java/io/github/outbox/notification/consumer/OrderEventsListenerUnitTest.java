package io.github.outbox.notification.consumer;

import io.github.dmitriyiliyov.oncebox.core.inbox.EventIdExtractor;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;
import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEvent;
import io.github.outbox.notification.notification.NotificationService;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventsListenerUnitTest {

    private static final UUID EVENT_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static final OrderEvent EVENT = new OrderCancelledEvent(7L, 42L, Instant.parse("2026-11-14T10:15:30Z"),
            "c-1", null);

    private static final String TRACE_PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Mock
    private NotificationService notificationService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Tracer tracer;

    private RecordingIdempotentConsumer idempotentConsumer;
    private OrderEventsListener tested;

    @BeforeEach
    void setUp() {
        idempotentConsumer = new RecordingIdempotentConsumer();
        tested = new OrderEventsListener(idempotentConsumer, notificationService, tracer);
    }

    @Test
    @DisplayName("UT onOrderEvent() when the event is new should notify about it under the id from its header")
    void onOrderEvent_whenEventIsNew_shouldNotifyAboutItUnderTheIdFromItsHeader() {
        // when
        tested.onOrderEvent(message(EVENT_ID));

        // then
        assertThat(idempotentConsumer.seen).containsExactly(EVENT_ID);
        verify(notificationService).notify(EVENT_ID, EVENT);
    }

    @Test
    @DisplayName("UT onOrderEvent() when the event was consumed before should notify nobody")
    void onOrderEvent_whenEventWasConsumedBefore_shouldNotifyNobody() {
        // given
        idempotentConsumer.seen.add(EVENT_ID);

        // when
        tested.onOrderEvent(message(EVENT_ID));

        // then
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("UT onOrderEvent() when the event is handled should name it in the log context and clear it after")
    void onOrderEvent_whenEventIsHandled_shouldNameItInLogContextAndClearItAfter() {
        // given
        Map<String, String> duringNotification = new HashMap<>();
        when(notificationService.notify(eq(EVENT_ID), any())).thenAnswer(invocation -> {
            duringNotification.putAll(MDC.getCopyOfContextMap());
            return Optional.empty();
        });

        // when
        tested.onOrderEvent(message(EVENT_ID));

        // then
        assertThat(duringNotification)
                .containsEntry("eventId", EVENT_ID.toString())
                .containsEntry("orderId", "7")
                .containsEntry("customerId", "42");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("UT onOrderEvent() when the event carries a valid trace parent should link the handle span to the causing request")
    void onOrderEvent_whenEventCarriesValidTraceParent_shouldLinkHandleSpanToTheCausingRequest() {
        // given
        OrderEvent tracedEvent = new OrderCancelledEvent(7L, 42L, Instant.parse("2026-11-14T10:15:30Z"),
                "c-1", TRACE_PARENT);

        // when
        tested.onOrderEvent(MessageBuilder.withPayload(tracedEvent)
                .setHeader("oncebox_event_id", EVENT_ID.toString())
                .build());

        // then
        Span.Builder spanBuilder = tracer.spanBuilder().name(OrderEventsListener.HANDLE_SPAN_NAME);
        verify(spanBuilder).addLink(org.mockito.ArgumentMatchers.argThat(link ->
                link instanceof Link
                        && "4bf92f3577b34da6a3ce929d0e0e4736".equals(link.getTraceContext().traceId())
                        && "00f067aa0ba902b7".equals(link.getTraceContext().spanId())));
    }

    @Test
    @DisplayName("UT onOrderEvent() when the event carries no trace parent should handle it in a span without links")
    void onOrderEvent_whenEventCarriesNoTraceParent_shouldHandleItInASpanWithoutLinks() {
        // when
        tested.onOrderEvent(message(EVENT_ID));

        // then
        verify(tracer.spanBuilder().name(OrderEventsListener.HANDLE_SPAN_NAME), never()).addLink(any(Link.class));
    }

    @Test
    @DisplayName("UT onOrderEvent() when the trace parent is malformed should handle the event without failing or linking")
    void onOrderEvent_whenTraceParentIsMalformed_shouldHandleTheEventWithoutFailingOrLinking() {
        // given
        OrderEvent malformed = new OrderCancelledEvent(7L, 42L, Instant.parse("2026-11-14T10:15:30Z"),
                "c-1", "not-a-traceparent");

        // when
        tested.onOrderEvent(MessageBuilder.withPayload(malformed)
                .setHeader("oncebox_event_id", EVENT_ID.toString())
                .build());

        // then
        verify(notificationService).notify(EVENT_ID, malformed);
        verify(tracer.spanBuilder().name(OrderEventsListener.HANDLE_SPAN_NAME), never()).addLink(any(Link.class));
    }

    private static Message<OrderEvent> message(UUID eventId) {
        return MessageBuilder.withPayload(EVENT).setHeader("oncebox_event_id", eventId.toString()).build();
    }

    /**
     * Runs the operation for an id it has not seen and skips it for one it has, as the inbox does.
     */
    private static final class RecordingIdempotentConsumer implements IdempotentConsumer {

        private final Set<UUID> seen = new HashSet<>();

        @Override
        public void consume(UUID eventId, Runnable operation) {
            if (seen.add(eventId)) {
                operation.run();
            }
        }

        @Override
        public <T> void consume(T message, EventIdExtractor<T> idExtractor, Consumer<T> operation) {
            consume(idExtractor.extract(message), () -> operation.accept(message));
        }

        @Override
        public void consume(Set<UUID> ids, Consumer<Set<UUID>> operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void consume(List<T> messages, EventIdExtractor<T> idExtractor, Consumer<List<T>> operation) {
            throw new UnsupportedOperationException();
        }
    }
}
