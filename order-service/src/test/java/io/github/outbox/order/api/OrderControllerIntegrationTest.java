package io.github.outbox.order.api;

import io.github.outbox.order.domain.OrderAlreadyCancelledException;
import io.github.outbox.order.domain.OrderNotFoundException;
import io.github.outbox.order.domain.OrderService;
import io.github.outbox.order.domain.OrderStatus;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

@WebMvcTest(OrderController.class)
@Import(OrderControllerIntegrationTest.FixedClockConfiguration.class)
class OrderControllerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:15:30Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService service;

    @Test
    @DisplayName("IT create() when the request is valid should answer 201 with the order and its location")
    void create_whenRequestIsValid_shouldAnswer201WithTheOrderAndItsLocation() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class), any())).thenReturn(createdOrder(7L));

        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.items[0].productId").value("sku-1"));
        verify(service).create(eq(new CreateOrderRequest(42L, List.of(
                new OrderItemRequest("sku-1", 2, new BigDecimal("10.50"))
        ))), notNull());
    }

    @Test
    @DisplayName("IT create() when a correlation id header is sent should pass it to the service")
    void create_whenCorrelationIdHeaderIsSent_shouldPassItToTheService() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class), any())).thenReturn(createdOrder(7L));

        // when
        mvc.perform(post("/api/v1/orders")
                        .header("X-Correlation-Id", "c-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                                """))
                .andExpect(status().isCreated());

        // then
        verify(service).create(any(CreateOrderRequest.class), eq("c-1"));
    }

    @Test
    @DisplayName("IT create() when the answer is an error should still carry the sent X-Correlation-Id response header")
    void create_whenAnswerIsAnError_shouldStillCarryTheCorrelationIdResponseHeader() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders")
                        .header("X-Correlation-Id", "c-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-Id", "c-9"));
    }

    @Test
    @DisplayName("IT create() when no correlation id is sent should answer with a generated X-Correlation-Id header")
    void create_whenNoCorrelationIdIsSent_shouldAnswerWithGeneratedCorrelationIdHeader() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class), any())).thenReturn(createdOrder(7L));

        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().string("X-Correlation-Id", notNullValue()))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    @Test
    @DisplayName("IT create() when the correlation id is too long should not echo it as the response header")
    void create_whenCorrelationIdIsTooLong_shouldNotEchoItInTheResponseHeader() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders")
                        .header("X-Correlation-Id", "c".repeat(129))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("X-Correlation-Id"));
    }

    @Test
    @DisplayName("IT create() when the correlation id header is too long should name the header in the problem")
    void create_whenCorrelationIdHeaderIsTooLong_shouldNameTheHeaderInTheProblem() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders")
                        .header("X-Correlation-Id", "c".repeat(129))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("X-Correlation-Id"))
                .andExpect(jsonPath("$.errors[0].message").value("X-Correlation-Id must be at most 128 characters"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when customerId is missing should answer a validation problem naming the field")
    void create_whenCustomerIdIsMissing_shouldAnswerValidationProblemNamingTheField() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/orders"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-14T10:15:30Z"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("customerId"))
                .andExpect(jsonPath("$.errors[0].message").value("customerId must not be null"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when items are empty should answer a validation problem naming items")
    void create_whenItemsAreEmpty_shouldAnswerValidationProblemNamingItems() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": []}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"))
                .andExpect(jsonPath("$.errors[0].message").value("items must not be empty"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when an item has zero quantity should name the field by its path in the body")
    void create_whenItemHasZeroQuantity_shouldNameTheFieldByItsPathInTheBody() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 0, "price": 10.50}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("quantity must be positive"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when a price has three fraction digits should answer 400 without reaching the service")
    void create_whenPriceHasThreeFractionDigits_shouldAnswer400WithoutReachingTheService() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 1, "price": 10.505}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].price"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when a price has 13 integer digits should name the price, as its total could not be stored")
    void create_whenPriceHas13IntegerDigits_shouldNameThePrice() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 1000, "price": 1000000000000.00}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].price"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("price must have at most 12 integer and 2 fraction digits"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the quantity is fractional should answer 400 instead of truncating it")
    void create_whenQuantityIsFractional_shouldAnswer400InsteadOfTruncatingIt() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2.9, "price": 10.50}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the quantity is sent as a string should answer 400 instead of converting it")
    void create_whenQuantityIsSentAsString_shouldAnswer400InsteadOfConvertingIt() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": "5", "price": 10.50}]}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the body is not JSON should answer Spring's 400 problem stamped with path")
    void create_whenBodyIsNotJson_shouldAnswerSpringsProblemStampedWithPath() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-14T10:15:30Z"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the body is not JSON media type should answer 415")
    void create_whenBodyIsNotJsonMediaType_shouldAnswer415() throws Exception {
        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.TEXT_PLAIN).content("order"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT create() when the service fails unexpectedly should answer 500 without the cause's message")
    void create_whenServiceFailsUnexpectedly_shouldAnswer500WithoutTheCausesMessage() throws Exception {
        // given
        when(service.create(any(CreateOrderRequest.class), any()))
                .thenThrow(new IllegalStateException("relation \"orders\" does not exist"));

        // when / then
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": 42, "items": [{"productId": "sku-1", "quantity": 2, "price": 10.50}]}
                        """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/internal-error"))
                .andExpect(jsonPath("$.detail").value("The operation could not be completed"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }

    @Test
    @DisplayName("IT get() when the order exists should answer 200 with the order")
    void get_whenOrderExists_shouldAnswer200WithTheOrder() throws Exception {
        // given
        when(service.get(7L)).thenReturn(createdOrder(7L));

        // when / then
        mvc.perform(get("/api/v1/orders/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.total").value(21.00))
                .andExpect(jsonPath("$.createdAt").value("2026-11-14T10:15:30Z"));
    }

    @Test
    @DisplayName("IT get() when the order does not exist should answer the order-not-found problem")
    void get_whenOrderDoesNotExist_shouldAnswerTheOrderNotFoundProblem() throws Exception {
        // given
        when(service.get(7L)).thenThrow(new OrderNotFoundException(7L));

        // when / then
        mvc.perform(get("/api/v1/orders/7"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/order-not-found"))
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.detail").value("Order 7 not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/7"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-14T10:15:30Z"));
    }

    @Test
    @DisplayName("IT get() when id is not positive should answer a validation problem naming id")
    void get_whenIdIsNotPositive_shouldAnswerValidationProblemNamingId() throws Exception {
        // when / then
        mvc.perform(get("/api/v1/orders/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("id must be positive"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT get() when id is not a number should answer 400 without reaching the service")
    void get_whenIdIsNotNumber_shouldAnswer400WithoutReachingTheService() throws Exception {
        // when / then
        mvc.perform(get("/api/v1/orders/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/v1/orders/abc"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("IT cancel() when the order is created should answer 200 with the cancelled order")
    void cancel_whenOrderIsCreated_shouldAnswer200WithTheCancelledOrder() throws Exception {
        // given
        when(service.cancel(eq(7L), any())).thenReturn(cancelledOrder(7L));

        // when / then
        mvc.perform(post("/api/v1/orders/7/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value("2026-11-14T10:15:30Z"));
    }

    @Test
    @DisplayName("IT cancel() when the order is already cancelled should answer the order-already-cancelled problem")
    void cancel_whenOrderIsAlreadyCancelled_shouldAnswerTheOrderAlreadyCancelledProblem() throws Exception {
        // given
        when(service.cancel(eq(7L), any())).thenThrow(new OrderAlreadyCancelledException(7L));

        // when / then
        mvc.perform(post("/api/v1/orders/7/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/order-already-cancelled"))
                .andExpect(jsonPath("$.title").value("Order already cancelled"))
                .andExpect(jsonPath("$.detail").value("Order 7 is already cancelled"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/7/cancel"));
    }

    @Test
    @DisplayName("IT cancel() when the order does not exist should answer the order-not-found problem")
    void cancel_whenOrderDoesNotExist_shouldAnswerTheOrderNotFoundProblem() throws Exception {
        // given
        when(service.cancel(eq(7L), any())).thenThrow(new OrderNotFoundException(7L));

        // when / then
        mvc.perform(post("/api/v1/orders/7/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://spring-transactional-outbox/errors/order-not-found"));
    }

    private static OrderResponse cancelledOrder(long id) {
        return new OrderResponse(
                id,
                42L,
                OrderStatus.CANCELLED,
                new BigDecimal("21.00"),
                NOW,
                NOW,
                List.of(new OrderItemResponse("sku-1", 2, new BigDecimal("10.50")))
        );
    }

    private static OrderResponse createdOrder(long id) {
        return new OrderResponse(
                id,
                42L,
                OrderStatus.CREATED,
                new BigDecimal("21.00"),
                NOW,
                null,
                List.of(new OrderItemResponse("sku-1", 2, new BigDecimal("10.50")))
        );
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
