package io.github.outbox.order.api;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Item of an order")
public record OrderItemRequest(

        @Schema(
                description = "Id of the product",
                example = "PROD-12345",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "productId must not be blank")
        @Size(max = 64, message = "productId must be at most {max} characters")
        String productId,

        @Schema(
                description = "Quantity of the product",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "1",
                maximum = "1000"
        )
        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be positive")
        @Max(value = 1000, message = "quantity must be at most {value}")
        Integer quantity,

        @Schema(
                description = "Unit price of the product",
                example = "10.50",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "0",
                maximum = "999999999999.99"
        )
        @NotNull(message = "price must not be null")
        @DecimalMin(value = "0.00", message = "price must not be negative")
        // 12 integer digits times the largest quantity and number of items keeps the total within NUMERIC(19, 2)
        @Digits(integer = 12, fraction = 2,
                message = "price must have at most {integer} integer and {fraction} fraction digits")
        BigDecimal price
) {
}
