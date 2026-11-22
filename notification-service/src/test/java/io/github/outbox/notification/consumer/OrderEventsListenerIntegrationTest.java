package io.github.outbox.notification.consumer;

import io.github.dmitriyiliyov.oncebox.core.inbox.EventIdExtractor;
import io.github.dmitriyiliyov.oncebox.core.inbox.IdempotentConsumer;
import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEvent;
import io.github.outbox.notification.notification.NotificationService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Runs the listener against a real OpenTelemetry tracer with an in-memory exporter, to prove the handling span
 * is a child of the listener's span and links to the trace of the request that caused the event.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventsListenerIntegrationTest {

    private static final UUID EVENT_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String TRACE_PARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

    @Mock
    private NotificationService notificationService;

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private Tracer tracer;
    private OrderEventsListener tested;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        tracer = new OtelTracer(tracerProvider.get("test"), new OtelCurrentTraceContext(), event -> {
        });
        tested = new OrderEventsListener(new RecordingIdempotentConsumer(), notificationService, tracer);
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    @DisplayName("IT onOrderEvent() when the event carries a valid trace parent should link the handle span to the trace of the causing request")
    void onOrderEvent_whenEventCarriesValidTraceParent_shouldLinkHandleSpanToTheCausingRequest() {
        // given
        OrderEvent event = event(TRACE_PARENT);
        Span listenerSpan = tracer.spanBuilder().name("order-events-listener").start();

        // when
        try (Tracer.SpanInScope ignored = tracer.withSpan(listenerSpan)) {
            tested.onOrderEvent(message(event));
        } finally {
            listenerSpan.end();
        }

        // then
        verify(notificationService).notify(EVENT_ID, event);
        SpanData handleSpan = spanNamed(OrderEventsListener.HANDLE_SPAN_NAME);
        assertThat(handleSpan.getParentSpanId()).isEqualTo(listenerSpan.context().spanId());
        assertThat(handleSpan.getLinks())
                .extracting(link -> link.getSpanContext().getTraceId() + "/" + link.getSpanContext().getSpanId())
                .containsExactly(TRACE_ID + "/" + SPAN_ID);
        LinkData link = handleSpan.getLinks().get(0);
        assertThat(link.getSpanContext().isRemote()).isTrue();
        assertThat(link.getSpanContext().isSampled()).isTrue();
    }

    @Test
    @DisplayName("IT onOrderEvent() when the event carries an unsampled trace parent should link to it as not sampled")
    void onOrderEvent_whenEventCarriesUnsampledTraceParent_shouldLinkToItAsNotSampled() {
        // given
        OrderEvent event = event("00-" + TRACE_ID + "-" + SPAN_ID + "-00");

        // when
        tested.onOrderEvent(message(event));

        // then
        SpanData handleSpan = spanNamed(OrderEventsListener.HANDLE_SPAN_NAME);
        assertThat(handleSpan.getLinks()).hasSize(1);
        assertThat(handleSpan.getLinks().get(0).getSpanContext().isSampled()).isFalse();
    }

    @Test
    @DisplayName("IT onOrderEvent() when the event carries no trace parent should handle it in a child span without links and without failing")
    void onOrderEvent_whenEventCarriesNoTraceParent_shouldHandleItInAChildSpanWithoutLinks() {
        // given
        OrderEvent event = event(null);
        Span listenerSpan = tracer.spanBuilder().name("order-events-listener").start();

        // when
        try (Tracer.SpanInScope ignored = tracer.withSpan(listenerSpan)) {
            tested.onOrderEvent(message(event));
        } finally {
            listenerSpan.end();
        }

        // then
        verify(notificationService).notify(EVENT_ID, event);
        SpanData handleSpan = spanNamed(OrderEventsListener.HANDLE_SPAN_NAME);
        assertThat(handleSpan.getParentSpanId()).isEqualTo(listenerSpan.context().spanId());
        assertThat(handleSpan.getLinks()).isEmpty();
    }

    @Test
    @DisplayName("IT onOrderEvent() when the trace parent is malformed should handle the event without links and without failing")
    void onOrderEvent_whenTraceParentIsMalformed_shouldHandleTheEventWithoutLinksAndWithoutFailing() {
        // given
        OrderEvent event = event("not-a-traceparent");

        // when
        tested.onOrderEvent(message(event));

        // then
        verify(notificationService).notify(EVENT_ID, event);
        assertThat(spanNamed(OrderEventsListener.HANDLE_SPAN_NAME).getLinks()).isEmpty();
    }

    private SpanData spanNamed(String name) {
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        return spans.stream()
                .filter(span -> span.getName().equals(name))
                .reduce((first, second) -> {
                    throw new AssertionError("more than one span named " + name);
                })
                .orElseThrow(() -> new AssertionError("no span named " + name + " among " + spans));
    }

    private static OrderEvent event(String traceParent) {
        return new OrderCancelledEvent(7L, 42L, Instant.parse("2026-11-14T10:15:30Z"), "c-1", traceParent);
    }

    private static Message<OrderEvent> message(OrderEvent event) {
        return MessageBuilder.withPayload(event).setHeader("oncebox_event_id", EVENT_ID.toString()).build();
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
