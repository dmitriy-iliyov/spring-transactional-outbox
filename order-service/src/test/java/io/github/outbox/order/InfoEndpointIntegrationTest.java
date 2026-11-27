package io.github.outbox.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = InfoEndpointIntegrationTest.TestApplication.class,
        properties = "management.endpoint.health.access=none")
@AutoConfigureMockMvc
class InfoEndpointIntegrationTest {

    @Autowired
    private MockMvc tested;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private GitProperties gitProperties;

    @Test
    @DisplayName("IT GET /actuator/info should report the service build and available Git metadata")
    void info_whenRequested_shouldReportBuildAndAvailableGitMetadata() throws Exception {
        // when
        String body = tested.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode info = objectMapper.readTree(body);

        // then
        assertThat(info.at("/build/artifact").asText()).isEqualTo("order-service");
        assertThat(info.at("/build/version").asText()).isNotBlank();
        assertThat(info.at("/build/time").asText()).isNotBlank();
        if (gitProperties == null) {
            assertThat(info.has("git")).isFalse();
        } else {
            assertThat(info.at("/git/branch").asText()).isNotBlank().isEqualTo(gitProperties.getBranch());
            assertThat(info.at("/git/commit/id").asText()).isNotBlank().isEqualTo(gitProperties.getShortCommitId());
            assertThat(Instant.parse(info.at("/git/commit/time").asText())).isEqualTo(gitProperties.getCommitTime());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, KafkaAutoConfiguration.class})
    static class TestApplication {
    }
}
