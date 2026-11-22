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
    @DisplayName("IT apiDocs() when the endpoint documents a server error should show the 500 as a problem detail")
    void apiDocs_whenEndpointDocumentsServerError_shouldShow500AsProblemDetail() throws Exception {
        // when
        JsonNode docs = apiDocs();

        // then
        JsonNode content = docs.path("paths").path("/api/v1/notifications").path("get").path("responses")
                .path("500").path("content");
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.path(MediaType.APPLICATION_PROBLEM_JSON_VALUE).path("schema").path("$ref").asText())
                .isEqualTo(PROBLEM_REF);
        assertThat(docs.path("paths").path("/api/v1/notifications").path("get").path("responses")
                .path("500").path("description").asText()).isNotBlank();
    }

    @Test
    @DisplayName("IT apiDocs() when the orderId parameter is documented should describe it and give an example")
    void apiDocs_whenOrderIdIsDocumented_shouldDescribeItAndGiveAnExample() throws Exception {
        // when
        JsonNode parameters = apiDocs().path("paths").path("/api/v1/notifications").path("get").path("parameters");

        // then
        assertThat(parameters.size()).isEqualTo(1);
        JsonNode orderId = parameters.get(0);
        assertThat(orderId.path("name").asText()).isEqualTo("orderId");
        assertThat(orderId.path("in").asText()).isEqualTo("query");
        assertThat(orderId.path("required").asBoolean()).isTrue();
        assertThat(orderId.path("description").asText()).isNotBlank();
        assertThat(orderId.path("example").asText()).isEqualTo("1");
        assertThat(orderId.path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(orderId.path("schema").path("format").asText()).isEqualTo("int64");
    }

    @Test
    @DisplayName("IT apiDocs() when the response schema is documented should give an example for every field")
    void apiDocs_whenResponseSchemaIsDocumented_shouldGiveAnExampleForEveryField() throws Exception {
        // when
        JsonNode properties = apiDocs().path("components").path("schemas").path("NotificationResponse")
                .path("properties");

        // then
        assertThat(properties.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "eventId", "orderId", "customerId", "type", "channel", "createdAt");
        JsonNode schemas = apiDocs().path("components").path("schemas");
        properties.properties().forEach(field -> {
            String name = field.getKey();
            JsonNode property = field.getValue();
            // enum fields are documented as a $ref (enumAsRef), so their description and example sit on the enum schema
            if (property.has("$ref")) {
                String ref = property.path("$ref").asText();
                String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
                assertThat(schemas.path(schemaName).path("description").asText())
                        .as("description of %s (%s)", name, schemaName).isNotBlank();
                assertThat(schemas.path(schemaName).path("example").isMissingNode())
                        .as("example of %s (%s)", name, schemaName).isFalse();
            } else {
                assertThat(property.path("description").asText()).as("description of %s", name).isNotBlank();
                assertThat(property.path("example").isMissingNode()).as("example of %s", name).isFalse();
            }
        });
        assertThat(properties.path("id").path("example").asText()).isEqualTo("1");
        assertThat(properties.path("eventId").path("example").asText())
                .isEqualTo("0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
        assertThat(properties.path("orderId").path("example").asText()).isEqualTo("7");
        assertThat(properties.path("customerId").path("example").asText()).isEqualTo("42");
        assertThat(schemas.path("NotificationType").path("example").asText()).isEqualTo("ORDER_CREATED");
        assertThat(schemas.path("NotificationChannel").path("example").asText()).isEqualTo("LOG");
        assertThat(properties.path("createdAt").path("example").asText()).isEqualTo("2026-11-14T10:15:30Z");
    }

    @Test
    @DisplayName("UT NotificationController should carry no springdoc annotations; NotificationApi should")
    void controller_shouldCarryNoSpringdocAnnotations_butTheApiInterfaceShould() throws Exception {
        // when
        var method = NotificationController.class.getMethod("findByOrderId", Long.class);

        // then
        for (var annotation : method.getAnnotations()) {
            assertThat(annotation.annotationType().getPackageName())
                    .as("annotation %s on the controller", annotation)
                    .doesNotStartWith("io.swagger");
        }
        assertThat(NotificationApi.class.getMethod("findByOrderId", Long.class)
                .getAnnotation(io.swagger.v3.oas.annotations.Operation.class)).isNotNull();
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
