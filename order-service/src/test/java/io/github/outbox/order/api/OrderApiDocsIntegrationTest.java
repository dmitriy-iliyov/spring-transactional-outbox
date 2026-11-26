package io.github.outbox.order.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.outbox.order.config.ClockConfiguration;
import io.github.outbox.order.config.OpenApiConfiguration;
import io.github.outbox.order.domain.OrderService;
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

// The OpenAPI document of the order API as springdoc serves it, compared with the endpoints the controller maps.
@WebMvcTest(OrderController.class)
@Import({OpenApiConfiguration.class, ClockConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class})
class OrderApiDocsIntegrationTest {

    private static final String PROBLEM_REF = "#/components/schemas/Problem";
    private static final String ORDER_REF = "#/components/schemas/OrderResponse";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService service;

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
    @DisplayName("IT apiDocs() when requested should document the success status and order body of each endpoint")
    void apiDocs_whenRequested_shouldDocumentSuccessStatusAndOrderBody() throws Exception {
        // when
        JsonNode paths = apiDocs().path("paths");

        // then
        assertSuccess(paths.path("/api/v1/orders").path("post"), "201");
        assertSuccess(paths.path("/api/v1/orders/{id}").path("get"), "200");
        assertSuccess(paths.path("/api/v1/orders/{id}/cancel").path("post"), "200");
        assertThat(paths.path("/api/v1/orders").path("post").path("responses").path("201").path("headers")
                .has("Location")).isTrue();
    }

    @Test
    @DisplayName("IT apiDocs() when requested should document order creation's rate limit and retry delay")
    void apiDocs_whenRequested_shouldDocumentOrderCreationRateLimitWithRetryAfter() throws Exception {
        // given
        String orderCreationPath = "/api/v1/orders";

        // when
        JsonNode response = apiDocs().path("paths").path(orderCreationPath).path("post").path("responses").path("429");

        // then
        assertThat(response.isMissingNode()).as("order creation's 429 response").isFalse();
        assertThat(response.path("description").asText()).isNotBlank();
        JsonNode retryAfter = response.path("headers").path("Retry-After");
        assertThat(retryAfter.isMissingNode()).as("Retry-After header").isFalse();
        assertThat(retryAfter.path("description").asText()).containsIgnoringCase("seconds");
        assertThat(retryAfter.path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(retryAfter.path("schema").path("format").asText()).isEqualTo("int64");
    }

    @Test
    @DisplayName("IT apiDocs() when an endpoint documents a client error should show it as a problem detail")
    void apiDocs_whenEndpointDocumentsClientError_shouldShowItAsProblemDetail() throws Exception {
        // when
        JsonNode docs = apiDocs();

        // then
        List<String> clientErrors = new ArrayList<>();
        docs.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation ->
                operation.getValue().path("responses").properties().forEach(response -> {
                    if (response.getKey().startsWith("4")) {
                        String where = "%s %s %s".formatted(operation.getKey(), path.getKey(), response.getKey());
                        clientErrors.add(where);
                        JsonNode content = response.getValue().path("content");
                        assertThat(content.size()).as("media types of %s", where).isEqualTo(1);
                        assertThat(content.path(MediaType.APPLICATION_PROBLEM_JSON_VALUE).path("schema")
                                .path("$ref").asText()).as("schema of %s", where).isEqualTo(PROBLEM_REF);
                    }
                })));
        assertThat(clientErrors).contains(
                "post /api/v1/orders 400",
                "post /api/v1/orders 429",
                "get /api/v1/orders/{id} 400",
                "get /api/v1/orders/{id} 404",
                "post /api/v1/orders/{id}/cancel 400",
                "post /api/v1/orders/{id}/cancel 404",
                "post /api/v1/orders/{id}/cancel 409"
        );
        assertThat(docs.path("components").path("schemas").has("Problem")).isTrue();
    }

    @Test
    @DisplayName("IT apiDocs() when requested should be OpenAPI 3.0 so that cancelledAt keeps its nullable flag")
    void apiDocs_whenRequested_shouldBeOpenApi30WithNullableCancelledAt() throws Exception {
        // when
        JsonNode docs = apiDocs();

        // then
        assertThat(docs.path("openapi").asText()).startsWith("3.0");
        assertThat(docs.path("components").path("schemas").path("OrderResponse").path("properties")
                .path("cancelledAt").path("nullable").asBoolean()).isTrue();
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
            if (handler.getBeanType() != OrderController.class) {
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

    private static void assertSuccess(JsonNode operation, String code) {
        JsonNode response = operation.path("responses").path(code);
        assertThat(response.isMissingNode()).as("response %s", code).isFalse();
        assertThat(response.path("content").path(MediaType.APPLICATION_JSON_VALUE).path("schema").path("$ref")
                .asText()).isEqualTo(ORDER_REF);
    }
}
