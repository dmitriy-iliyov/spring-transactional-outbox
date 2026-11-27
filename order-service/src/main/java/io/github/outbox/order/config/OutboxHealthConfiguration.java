package io.github.outbox.order.config;

import io.github.outbox.order.health.OutboxBacklogReader;
import io.github.outbox.order.health.OutboxHealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxHealthProperties.class)
public class OutboxHealthConfiguration {

    // The bean name is the component's name under /actuator/health: outbox. It stays out of the readiness group,
    // like Kafka: orders are still accepted while publishing lags.
    @Bean
    public OutboxHealthIndicator outbox(JdbcClient jdbc, Clock clock, OutboxHealthProperties properties) {
        return new OutboxHealthIndicator(new OutboxBacklogReader(jdbc, clock), properties);
    }
}
