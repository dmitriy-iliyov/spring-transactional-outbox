package io.github.outbox.notification.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots only the web + actuator slice (no database, no Kafka) and checks that {@code /actuator/info} answers with
 * the build info the build wrote into {@code META-INF/build-info.properties}; the full application exposes it next
 * to health and prometheus in {@code application.yml}.
 */
@SpringBootTest(
        classes = ActuatorInfoIntegrationTest.ActuatorOnlyApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "management.endpoints.web.exposure.include=health,info",
                // the main application.yml adds db to the readiness group; this slice has no database
                "management.endpoint.health.group.readiness.include=readinessState"
        }
)
@AutoConfigureMockMvc
class ActuatorInfoIntegrationTest {

    @SpringBootConfiguration
    // the oncebox starter registers no auto-configuration, so the slice stays free of database and Kafka
    @EnableAutoConfiguration(
            exclude = {DataSourceAutoConfiguration.class, LiquibaseAutoConfiguration.class, KafkaAutoConfiguration.class}
    )
    static class ActuatorOnlyApp {
    }

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("IT GET /actuator/info should answer 200 with the build info generated at build time")
    void info_whenCalled_shouldAnswer200WithBuildInfo() throws Exception {
        // when / then
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.artifact").value("notification-service"))
                .andExpect(jsonPath("$.build.name").value("notification-service"))
                .andExpect(jsonPath("$.build.version").isNotEmpty());
    }

    @Test
    @DisplayName("IT GET /actuator/info when built from a clone should also carry the git info")
    void info_whenBuiltFromAClone_shouldCarryGitInfo() throws Exception {
        // when / then: the Maven build runs from a clone, so git.properties exists on the test classpath
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.git.branch").isNotEmpty())
                .andExpect(jsonPath("$.git.commit.id").isNotEmpty());
    }
}
