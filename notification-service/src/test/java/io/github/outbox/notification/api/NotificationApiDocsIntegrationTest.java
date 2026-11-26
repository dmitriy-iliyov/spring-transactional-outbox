package io.github.outbox.notification.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.outbox.notification.config.ClockConfiguration;
import io.github.outbox.notification.config.OpenApiConfiguration;
import io.github.outbox.notification.notification.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The OpenAPI document of the notifications API as springdoc serves it, held to the endpoints the controller maps.
@WebMvcTest(NotificationController.class)
@Import({OpenApiConfiguration.class, ClockConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class})
class NotificationApiDocsIntegrationTest {

    private static final String PROBLEM_REF = "#/components/schemas/Problem";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService service;

    @Test
    @DisplayName("IT apiDocs() when requested should list every endpoint the controller maps, with a summary")
    void apiDocs_whenRequested_shouldListEveryMappedEndpointWithSummary() throws Exception {
        // given
        Map<String, List<String>> mapped = controllerEndpoints();

        // when
        JsonNode paths = apiDocs().path("paths");

        // then
        assertThat(mapped).isNotEmpty();
        mapped.forEach((path, methods) -> methods.forEach(method -> {
            JsonNode operation = paths.path(path).path(method);
            assertThat(operation.isMissingNode()).as("%s %s is documented", method, path).isFalse();
            assertThat(operation.path("summary").asText()).as("summary of %s %s", method, path).isNotBlank();
        }));
    }

    @Test
    @DisplayName("IT apiDocs() when requested should document the notification list as the success body")
    void apiDocs_whenRequested_shouldDocumentNotificationListAsSuccessBody() throws Exception {
        // when
        JsonNode response = apiDocs().path("paths").path("/api/v1/notifications").path("get")
                .path("responses").path("200");

        // then
        JsonNode schema = response.path("content").path(MediaType.APPLICATION_JSON_VALUE).path("schema");
        assertThat(schema.path("type").asText()).isEqualTo("array");
        assertThat(schema.path("items").path("$ref").asText())
                .isEqualTo("#/components/schemas/NotificationResponse");
    }

    @Test
    @DisplayName("IT apiDocs() when requested should document the notification lookup and its responses")
    void apiDocs_whenRequested_shouldDocumentNotificationLookupAndItsResponses() throws Exception {
        // when
        JsonNode operation = apiDocs().path("paths").path("/api/v1/notifications/{id}").path("get");

        // then
        assertThat(operation.isMissingNode()).as("GET /api/v1/notifications/{id} is documented").isFalse();
        JsonNode responses = operation.path("responses");
        JsonNode schema = responses.path("200").path("content").path(MediaType.APPLICATION_JSON_VALUE).path("schema");
        assertThat(schema.path("$ref").asText()).isEqualTo("#/components/schemas/NotificationResponse");
        assertThat(schema.path("type").asText()).isNotEqualTo("array");
        assertThat(schema.has("items")).isFalse();
        for (String code : List.of("400", "404")) {
            JsonNode problem = responses.path(code).path("content").path(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                    .path("schema");
            assertThat(problem.path("$ref").asText()).as("response %s documents a problem detail", code)
                    .isEqualTo(PROBLEM_REF);
        }
    }

    @Test
    @DisplayName("IT apiDocs() when the endpoint documents a client error should show it as a problem detail")
    void apiDocs_whenEndpointDocumentsClientError_shouldShowItAsProblemDetail() throws Exception {
        // when
        JsonNode docs = apiDocs();

        // then
        JsonNode content = docs.path("paths").path("/api/v1/notifications").path("get").path("responses")
                .path("400").path("content");
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.path(MediaType.APPLICATION_PROBLEM_JSON_VALUE).path("schema").path("$ref").asText())
                .isEqualTo(PROBLEM_REF);
        assertThat(docs.path("components").path("schemas").has("Problem")).isTrue();
    }

    @Test
    @DisplayName("IT apiDocs() when requested should be OpenAPI 3.0")
    void apiDocs_whenRequested_shouldBeOpenApi30() throws Exception {
        // when
        JsonNode docs = apiDocs();

        // then
        assertThat(docs.path("openapi").asText()).startsWith("3.0");
    }

    @Test
    @DisplayName("IT apiDocs() when requested should describe the required order id query parameter")
    void apiDocs_whenRequested_shouldDescribeRequiredOrderIdQueryParameter() throws Exception {
        // when
        JsonNode parameters = apiDocs().path("paths").path("/api/v1/notifications").path("get").path("parameters");

        // then
        List<JsonNode> orderIds = new ArrayList<>();
        parameters.forEach(parameter -> {
            if (parameter.path("name").asText().equals("orderId")) {
                orderIds.add(parameter);
            }
        });
        assertThat(orderIds).singleElement().satisfies(parameter -> {
            assertThat(parameter.path("in").asText()).isEqualTo("query");
            assertThat(parameter.path("description").asText()).isNotBlank();
            assertThat(parameter.path("required").asBoolean()).isTrue();
            assertThat(parameter.path("example").asText()).isEqualTo("1");
        });
    }

    @Test
    @DisplayName("IT apiDocs() when requested should document an internal error as a problem detail")
    void apiDocs_whenRequested_shouldDocumentInternalErrorAsProblemDetail() throws Exception {
        // when
        JsonNode responses = apiDocs().path("paths").path("/api/v1/notifications").path("get").path("responses");

        // then
        assertThat(responses.has("200")).isTrue();
        assertThat(responses.has("400")).isTrue();
        JsonNode content = responses.path("500").path("content");
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.path(MediaType.APPLICATION_PROBLEM_JSON_VALUE).path("schema").path("$ref").asText())
                .isEqualTo(PROBLEM_REF);
    }

    @Test
    @DisplayName("IT apiDocs() when requested should show examples for every notification response field")
    void apiDocs_whenRequested_shouldShowExamplesForEveryNotificationResponseField() throws Exception {
        // when
        JsonNode docs = apiDocs();
        JsonNode properties = docs.path("components").path("schemas").path("NotificationResponse")
                .path("properties");

        // then
        assertThat(properties.path("id").path("example").asText()).isEqualTo("1");
        assertThat(properties.path("eventId").path("example").asText())
                .isEqualTo("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
        assertThat(properties.path("orderId").path("example").asText()).isEqualTo("1");
        assertThat(properties.path("customerId").path("example").asText()).isEqualTo("42");
        assertThat(resolveSchema(docs, properties.path("type")).path("example").asText()).isEqualTo("ORDER_CREATED");
        assertThat(resolveSchema(docs, properties.path("channel")).path("example").asText()).isEqualTo("LOG");
        assertThat(properties.path("createdAt").path("example").asText()).isEqualTo("2026-11-14T10:15:30Z");
    }

    private static JsonNode resolveSchema(JsonNode docs, JsonNode schema) {
        return schema.has("$ref") ? docs.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private JsonNode apiDocs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private Map<String, List<String>> controllerEndpoints() {
        Map<String, List<String>> endpoints = new HashMap<>();
        handlerMapping.getHandlerMethods().forEach((RequestMappingInfo info, HandlerMethod handler) -> {
            if (handler.getBeanType() != NotificationController.class) {
                return;
            }
            for (String path : info.getPatternValues()) {
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    endpoints.computeIfAbsent(path, key -> new ArrayList<>()).add(method.name().toLowerCase());
                }
            }
        });
        return endpoints;
    }
}
