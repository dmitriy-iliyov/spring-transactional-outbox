package io.github.outbox.notification.api;

import io.github.outbox.notification.notification.NotificationChannel;
import io.github.outbox.notification.notification.NotificationService;
import io.github.outbox.notification.notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(NotificationControllerIntegrationTest.FixedClockConfiguration.class)
class NotificationControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:15:30Z");
    private static final UUID EVENT_ID = UUID.fromString("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationService service;

    @Test
    @DisplayName("IT findByOrderId() when the order has notifications should answer 200 with them")
    void findByOrderId_whenOrderHasNotifications_shouldAnswer200WithThem() throws Exception {
        // given
        when(service.findByOrderId(7L)).thenReturn(List.of(new NotificationResponse(
                1L, EVENT_ID, 7L, 42L, NotificationType.ORDER_CREATED, NotificationChannel.LOG, NOW)));

        // when / then
        mvc.perform(get("/api/v1/notifications").param("orderId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventId").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$[0].type").value("ORDER_CREATED"))
                .andExpect(jsonPath("$[0].channel").value("LOG"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-14T10:15:30Z"));
    }

    @Test
    @DisplayName("IT findByOrderId() when the order has no notifications should answer 200 with an empty list")
    void findByOrderId_whenOrderHasNoNotifications_shouldAnswer200WithEmptyList() throws Exception {
        // given
        when(service.findByOrderId(7L)).thenReturn(List.of());

        // when / then
        mvc.perform(get("/api/v1/notifications").param("orderId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("IT findByOrderId() when orderId is not positive should answer a validation problem naming it")
    void findByOrderId_whenOrderIdIsNotPositive_shouldAnswerValidationProblemNamingIt() throws Exception {
        // when / then
        mvc.perform(get("/api/v1/notifications").param("orderId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("orderId"))
                .andExpect(jsonPath("$.errors[0].message").value("orderId must be positive"))
                .andExpect(jsonPath("$.path").value("/api/v1/notifications"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-14T10:15:30Z"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT findByOrderId() when orderId is missing should answer Spring's 400 problem stamped with path")
    void findByOrderId_whenOrderIdIsMissing_shouldAnswerSpringsProblemStampedWithPath() throws Exception {
        // when / then
        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.path").value("/api/v1/notifications"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT findByOrderId() when the service fails unexpectedly should answer 500 without the cause's message")
    void findByOrderId_whenServiceFailsUnexpectedly_shouldAnswer500WithoutTheCausesMessage() throws Exception {
        // given
        when(service.findByOrderId(7L)).thenThrow(new IllegalStateException("relation \"notifications\" does not exist"));

        // when / then
        mvc.perform(get("/api/v1/notifications").param("orderId", "7"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/internal-error"))
                .andExpect(jsonPath("$.detail").value("The operation could not be completed"))
                .andExpect(jsonPath("$.path").value("/api/v1/notifications"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-14T10:15:30Z"))
                .andExpect(content().string(not(containsString("relation \"notifications\" does not exist"))));
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
