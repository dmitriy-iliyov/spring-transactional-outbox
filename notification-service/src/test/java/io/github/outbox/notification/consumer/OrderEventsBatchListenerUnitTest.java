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
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Runs the batch listener against an inbox that behaves as the real one does - a batch is one transaction, so a
 * failure stores none of its events - to prove the fallback reruns them one by one and reports the index of the
 * event that still fails.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventsBatchListenerUnitTest {

    private static final UUID FIRST_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static final UUID SECOND_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5c");
    private static final UUID THIRD_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5d");

    @Mock
    private NotificationService notificationService;

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private Tracer tracer;
    private RecordingIdempotentConsumer inbox;
    private OrderEventsBatchListener tested;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        tracer = new OtelTracer(tracerProvider.get("test"), new OtelCurrentTraceContext(), event -> {
        });
        inbox = new RecordingIdempotentConsumer();
        tested = new OrderEventsBatchListener(inbox, notificationService, tracer);
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    @DisplayName("UT onOrderEvents() when a batch arrives should hand it to the inbox once and notify every event")
    void onOrderEvents_whenBatchArrives_shouldHandItToTheInboxOnceAndNotifyEveryEvent() {
        // given
        List<Message<OrderEvent>> batch = batch(FIRST_ID, SECOND_ID, THIRD_ID);

        // when
        tested.onOrderEvents(batch);

        // then
        assertThat(inbox.batches).isEqualTo(1);
        verify(notificationService, times(3)).notify(any(UUID.class), any(OrderEvent.class));
        assertThat(inbox.stored).containsExactlyInAnyOrder(FIRST_ID, SECOND_ID, THIRD_ID);
    }

    @Test
    @DisplayName("UT onOrderEvents() when the batch carries an event already consumed should notify only the others")
    void onOrderEvents_whenBatchCarriesAnEventAlreadyConsumed_shouldNotifyOnlyTheOthers() {
        // given
        inbox.stored.add(SECOND_ID);
        List<Message<OrderEvent>> batch = batch(FIRST_ID, SECOND_ID, THIRD_ID);

        // when
        tested.onOrderEvents(batch);

        // then
        verify(notificationService, times(2)).notify(any(UUID.class), any(OrderEvent.class));
        verify(notificationService, never()).notify(org.mockito.ArgumentMatchers.eq(SECOND_ID), any(OrderEvent.class));
        assertThat(inbox.stored).containsExactlyInAnyOrder(FIRST_ID, SECOND_ID, THIRD_ID);
    }

    @Test
    @DisplayName("UT onOrderEvents() when the same event is twice in the batch should notify it once")
    void onOrderEvents_whenTheSameEventIsTwiceInTheBatch_shouldNotifyItOnce() {
        // given
        List<Message<OrderEvent>> batch = List.of(message(FIRST_ID), message(FIRST_ID), message(SECOND_ID));

        // when
        tested.onOrderEvents(batch);

        // then
        verify(notificationService, times(2)).notify(any(UUID.class), any(OrderEvent.class));
        assertThat(inbox.stored).containsExactlyInAnyOrder(FIRST_ID, SECOND_ID);
    }

    @Test
    @DisplayName("UT onOrderEvents() when the batch fails should rerun its events one by one, keep the ones before the failure and report its index")
    void onOrderEvents_whenBatchFails_shouldRerunOneByOneKeepTheOnesBeforeAndReportTheFailingIndex() {
        // given
        List<Message<OrderEvent>> batch = batch(FIRST_ID, SECOND_ID, THIRD_ID);
        failNotificationsFor(SECOND_ID);

        // when / then
        assertThatThrownBy(() -> tested.onOrderEvents(batch))
                .isInstanceOf(BatchListenerFailedException.class)
                .satisfies(failure -> {
                    BatchListenerFailedException exception = (BatchListenerFailedException) failure;
                    assertThat(exception.getIndex()).isEqualTo(1);
                    assertThat(exception.getCause()).hasMessage("insert failed");
                });
        // the batch as a whole stored nothing; the rerun stored the event before the failure
        assertThat(inbox.stored).containsExactly(FIRST_ID);
        assertThat(inbox.batches).isEqualTo(1);
        assertThat(inbox.records).isEqualTo(2);
    }

    @Test
    @DisplayName("UT onOrderEvents() when the batch fails on an event already consumed should skip it and handle the rest")
    void onOrderEvents_whenBatchFailsOnAnEventAlreadyConsumed_shouldSkipItAndHandleTheRest() {
        // given
        inbox.stored.add(FIRST_ID);
        List<Message<OrderEvent>> batch = batch(FIRST_ID, SECOND_ID, THIRD_ID);
        failNotificationsFor(SECOND_ID);

        // when / then
        assertThatThrownBy(() -> tested.onOrderEvents(batch))
                .isInstanceOf(BatchListenerFailedException.class);
        // the event stored before the poll is filtered out of the batch and skipped in the rerun; the failing one is
        // reported, and the one after it waits for the retry that starts at the reported index
        verify(notificationService, never()).notify(org.mockito.ArgumentMatchers.eq(FIRST_ID), any(OrderEvent.class));
        verify(notificationService, times(2)).notify(org.mockito.ArgumentMatchers.eq(SECOND_ID), any(OrderEvent.class));
        verify(notificationService, never()).notify(org.mockito.ArgumentMatchers.eq(THIRD_ID), any(OrderEvent.class));
        assertThat(inbox.stored).containsExactly(FIRST_ID);
    }

    @Test
    @DisplayName("UT onOrderEvents() when a batch is handled should give every event a span of its own")
    void onOrderEvents_whenBatchIsHandled_shouldGiveEveryEventASpanOfItsOwn() {
        // given
        List<Message<OrderEvent>> batch = batch(FIRST_ID, SECOND_ID, THIRD_ID);
        Span listenerSpan = tracer.spanBuilder().name("order-events-listener").start();

        // when
        try (Tracer.SpanInScope ignored = tracer.withSpan(listenerSpan)) {
            tested.onOrderEvents(batch);
        } finally {
            listenerSpan.end();
        }

        // then
        List<SpanData> handleSpans = spanExporter.getFinishedSpanItems().stream()
                .filter(span -> span.getName().equals(OrderEventsBatchListener.HANDLE_SPAN_NAME))
                .toList();
        assertThat(handleSpans).hasSize(3);
        assertThat(handleSpans).extracting(SpanData::getParentSpanId)
                .containsOnly(listenerSpan.context().spanId());
        assertThat(handleSpans).extracting(SpanData::getSpanId).doesNotHaveDuplicates();
    }

    /**
     * Fails the notification of one event only: a strict mock would reject the calls of its neighbours as
     * unexpected, so the failure is matched by the event id instead of by the arguments of a stub.
     */
    private void failNotificationsFor(UUID eventId) {
        doAnswer(invocation -> {
            if (eventId.equals(invocation.getArgument(0))) {
                throw new IllegalStateException("insert failed");
            }
            return java.util.Optional.empty();
        }).when(notificationService).notify(any(UUID.class), any(OrderEvent.class));
    }

    private static List<Message<OrderEvent>> batch(UUID... eventIds) {
        List<Message<OrderEvent>> messages = new ArrayList<>();
        for (UUID eventId : eventIds) {
            messages.add(message(eventId));
        }
        return messages;
    }

    private static Message<OrderEvent> message(UUID eventId) {
        // the correlation id tells the payloads apart, so a stub on one event does not answer for its neighbours
        OrderEvent event = new OrderCancelledEvent(7L, 42L, Instant.parse("2026-11-14T10:15:30Z"), "c-" + eventId, null);
        return MessageBuilder
                .withPayload(event)
                .setHeader("oncebox_event_id", eventId.toString())
                .build();
    }

    /**
     * One batch, one transaction: ids are recorded only if the operation returns, and a record is handled on its own
     * in a transaction of its own, exactly as the inbox does.
     */
    private static final class RecordingIdempotentConsumer implements IdempotentConsumer {

        private final Set<UUID> stored = new HashSet<>();
        private int batches;
        private int records;

        @Override
        public void consume(UUID eventId, Runnable operation) {
            if (!stored.contains(eventId)) {
                operation.run();
                stored.add(eventId);
            }
        }

        @Override
        public <T> void consume(T message, EventIdExtractor<T> idExtractor, Consumer<T> operation) {
            records++;
            consume(idExtractor.extract(message), () -> operation.accept(message));
        }

        @Override
        public void consume(Set<UUID> ids, Consumer<Set<UUID>> operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void consume(List<T> messages, EventIdExtractor<T> idExtractor, Consumer<List<T>> operation) {
            batches++;
            List<T> notConsumed = new ArrayList<>();
            Set<UUID> inBatch = new HashSet<>();
            for (T message : messages) {
                UUID eventId = idExtractor.extract(message);
                if (!stored.contains(eventId) && inBatch.add(eventId)) {
                    notConsumed.add(message);
                }
            }
            operation.accept(notConsumed);
            notConsumed.forEach(message -> stored.add(idExtractor.extract(message)));
        }
    }
}
