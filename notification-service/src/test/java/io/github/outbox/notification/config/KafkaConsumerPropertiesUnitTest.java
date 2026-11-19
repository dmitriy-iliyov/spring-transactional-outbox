package io.github.outbox.notification.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerPropertiesUnitTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("UT deadLetterTopic() when the order events topic is set should append the -dlt suffix")
    void deadLetterTopic_whenOrderEventsTopicIsSet_shouldAppendTheDltSuffix() {
        // given
        KafkaConsumerProperties properties = validProperties();

        // when
        String deadLetterTopic = properties.deadLetterTopic();

        // then
        assertThat(deadLetterTopic).isEqualTo("order-events-dlt");
    }

    @Test
    @DisplayName("UT validate() when the topic is blank should report a violation naming orderEventsTopic")
    void validate_whenTopicIsBlank_shouldReportOrderEventsTopic() {
        // given
        KafkaConsumerProperties properties = new KafkaConsumerProperties("   ", 3, Duration.ofSeconds(1),
                validTransientRetry());

        // when
        Set<ConstraintViolation<KafkaConsumerProperties>> violations = VALIDATOR.validate(properties);

        // then
        assertThat(propertyPaths(violations)).containsExactly("orderEventsTopic");
    }

    @Test
    @DisplayName("UT validate() when retries are negative should report a violation naming retries")
    void validate_whenRetriesAreNegative_shouldReportRetries() {
        // given
        KafkaConsumerProperties properties = new KafkaConsumerProperties("order-events", -1, Duration.ofSeconds(1),
                validTransientRetry());

        // when
        Set<ConstraintViolation<KafkaConsumerProperties>> violations = VALIDATOR.validate(properties);

        // then
        assertThat(propertyPaths(violations)).containsExactly("retries");
    }

    @Test
    @DisplayName("UT validate() when retryInterval is missing should report a violation naming retryInterval")
    void validate_whenRetryIntervalIsMissing_shouldReportRetryInterval() {
        // given
        KafkaConsumerProperties properties = new KafkaConsumerProperties("order-events", 3, null,
                validTransientRetry());

        // when
        Set<ConstraintViolation<KafkaConsumerProperties>> violations = VALIDATOR.validate(properties);

        // then
        assertThat(propertyPaths(violations)).containsExactly("retryInterval");
    }

    @Test
    @DisplayName("UT validate() when transientRetry is missing should report a violation naming transientRetry")
    void validate_whenTransientRetryIsMissing_shouldReportTransientRetry() {
        // given
        KafkaConsumerProperties properties = new KafkaConsumerProperties("order-events", 3, Duration.ofSeconds(1),
                null);

        // when
        Set<ConstraintViolation<KafkaConsumerProperties>> violations = VALIDATOR.validate(properties);

        // then
        assertThat(propertyPaths(violations)).containsExactly("transientRetry");
    }

    @Test
    @DisplayName("UT validate() when an interval inside transientRetry is missing should report its nested path")
    void validate_whenTransientIntervalIsMissing_shouldReportNestedPath() {
        // given
        KafkaConsumerProperties properties = new KafkaConsumerProperties("order-events", 3, Duration.ofSeconds(1),
                new KafkaConsumerProperties.TransientRetry(null, Duration.ofSeconds(30)));

        // when
        Set<ConstraintViolation<KafkaConsumerProperties>> violations = VALIDATOR.validate(properties);

        // then
        assertThat(propertyPaths(violations)).containsExactly("transientRetry.initialInterval");
    }

    @Test
    @DisplayName("UT validate() when all settings are valid should report no violations")
    void validate_whenAllSettingsAreValid_shouldReportNoViolations() {
        // given
        KafkaConsumerProperties properties = validProperties();

        // when
        Set<ConstraintViolation<KafkaConsumerProperties>> violations = VALIDATOR.validate(properties);

        // then
        assertThat(violations).isEmpty();
    }

    private static KafkaConsumerProperties validProperties() {
        return new KafkaConsumerProperties("order-events", 3, Duration.ofSeconds(1), validTransientRetry());
    }

    private static KafkaConsumerProperties.TransientRetry validTransientRetry() {
        return new KafkaConsumerProperties.TransientRetry(Duration.ofMillis(500), Duration.ofSeconds(30));
    }

    private static java.util.List<String> propertyPaths(Set<ConstraintViolation<KafkaConsumerProperties>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).toList();
    }
}
