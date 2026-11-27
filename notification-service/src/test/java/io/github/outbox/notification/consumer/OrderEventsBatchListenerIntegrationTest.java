package io.github.outbox.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.outbox.contract.OrderCancelledEvent;
import io.github.outbox.contract.OrderEventTypes;
import io.github.outbox.notification.NotificationServiceApplication;
import io.github.outbox.notification.notification.NotificationService;
import io.github.outbox.notification.support.RequiresDocker;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service started as in production over a real Kafka and Postgres, with the batch listener taking the topic.
 * The publisher plays the order service: every record carries the payload as JSON and the event id, type and payload
 * class in the oncebox headers, exactly as its outbox publishes them.
 */
@Testcontainers
@RequiresDocker
@SpringBootTest(
        classes = NotificationServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.type=batch",
                "spring.kafka.consumer.group-id=it-batch-order-events",
                "app.kafka.order-events-topic=it-batch-order-events",
                "app.kafka.retries=1",
                "app.kafka.retry-interval=2s",
                "app.kafka.transient-retry.initial-interval=1s",
                "app.kafka.transient-retry.max-interval=5s"
        })
class OrderEventsBatchListenerIntegrationTest {

    private static final String TOPIC = "it-batch-order-events";
    private static final String DEAD_LETTER_TOPIC = TOPIC + "-dlt";
    private static final Duration DELIVERY = Duration.ofSeconds(30);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private NotificationService notifications;

    @Autowired
    private DataSource dataSource;

    private KafkaProducer<String, String> producer;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(properties);
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("IT onOrderEvents() when the same event is delivered twice should store one notification")
    void onOrderEvents_whenTheSameEventIsDeliveredTwice_shouldStoreOneNotification() {
        // given
        UUID eventId = UUID.randomUUID();
        long orderId = 1001L;

        // when - both copies may land in the same poll or in two of them; either way the inbox records the id once
        publish(eventId, orderId, "duplicate");
        publish(eventId, orderId, "duplicate");

        // then
        await(() -> notifications.findByOrderId(orderId).size() == 1);
        assertThat(notifications.findByOrderId(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("IT onOrderEvents() when an event of a poll keeps failing should store the others and send it to the dead letter topic")
    void onOrderEvents_whenAnEventKeepsFailing_shouldStoreTheOthersAndSendItToTheDeadLetterTopic() {
        // given - the event in the middle can never be stored
        long firstOrder = 2001L;
        long poisonOrder = 2002L;
        long lastOrder = 2003L;
        UUID poisonId = UUID.randomUUID();
        try (AlwaysFailingInsert fault = AlwaysFailingInsert.forOrder(dataSource, poisonOrder)) {
            // when
            publish(UUID.randomUUID(), firstOrder, "first");
            publish(poisonId, poisonOrder, "poison");
            publish(UUID.randomUUID(), lastOrder, "last");

            // then
            await(() -> notifications.findByOrderId(firstOrder).size() == 1);
            await(() -> notifications.findByOrderId(lastOrder).size() == 1);
            await(() -> !deadLettersContaining("\"orderId\":" + poisonOrder).isEmpty());
            assertThat(fault.attempts()).as("inserts of the poison notification, the failed ones included")
                    .isGreaterThan(1);
            assertThat(notifications.findByOrderId(poisonOrder)).isEmpty();
        }
    }

    private void publish(UUID eventId, long orderId, String correlationId) {
        OrderCancelledEvent event = new OrderCancelledEvent(
                orderId, 42L, Instant.parse("2026-11-14T10:15:30Z"), correlationId, null);
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, payload(event));
        header(record, "oncebox_event_id", eventId.toString());
        header(record, "oncebox_event_type", OrderEventTypes.ORDER_CANCELLED);
        header(record, "oncebox_event_payload_type", event.getClass().getName());
        try {
            producer.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException("Event could not be published", e);
        }
    }

    private String payload(OrderCancelledEvent event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Event could not be serialized", e);
        }
    }

    private static void header(ProducerRecord<String, String> record, String key, String value) {
        record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads the dead letter topic from the start under a group of its own, and returns every record whose payload
     * carries the marker.
     */
    private List<String> deadLettersContaining(String marker) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "it-dlt-probe-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<String> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(DEAD_LETTER_TOPIC));
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (record.value() != null && record.value().contains(marker)) {
                    found.add(record.value());
                }
            }
        }
        return found;
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + DELIVERY.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting", e);
            }
        }
        throw new AssertionError("Condition not fulfilled within " + DELIVERY);
    }

    /**
     * Fails every insert of a notification for one order, from inside the service's own database, so the event
     * cannot be stored however often it is retried. The attempts are counted by a sequence, which a rollback does
     * not undo, so the count holds the failed attempts as well.
     */
    private static final class AlwaysFailingInsert implements AutoCloseable {

        private final JdbcClient jdbc;
        private final String name;

        private AlwaysFailingInsert(DataSource dataSource, long orderId) {
            this.jdbc = JdbcClient.create(dataSource);
            this.name = "it_always_fault_%d".formatted(orderId);
            jdbc.sql("CREATE SEQUENCE %s".formatted(name)).update();
            jdbc.sql("""
                    CREATE FUNCTION %1$s() RETURNS trigger AS $$
                    BEGIN
                        IF NEW.order_id = %2$d THEN
                            PERFORM nextval('%1$s');
                            RAISE EXCEPTION 'injected permanent failure for order %2$d';
                        END IF;
                        RETURN NEW;
                    END
                    $$ LANGUAGE plpgsql
                    """.formatted(name, orderId)).update();
            jdbc.sql("CREATE TRIGGER %1$s BEFORE INSERT ON notifications FOR EACH ROW EXECUTE FUNCTION %1$s()"
                    .formatted(name)).update();
        }

        static AlwaysFailingInsert forOrder(DataSource dataSource, long orderId) {
            return new AlwaysFailingInsert(dataSource, orderId);
        }

        long attempts() {
            return jdbc.sql("SELECT CASE WHEN is_called THEN last_value ELSE 0 END FROM %s".formatted(name))
                    .query(Long.class)
                    .single();
        }

        @Override
        public void close() {
            jdbc.sql("DROP TRIGGER IF EXISTS %1$s ON notifications".formatted(name)).update();
            jdbc.sql("DROP FUNCTION IF EXISTS %1$s()".formatted(name)).update();
            jdbc.sql("DROP SEQUENCE IF EXISTS %1$s".formatted(name)).update();
        }
    }
}
