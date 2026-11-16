package io.github.outbox.order.api;

import io.github.outbox.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Item of an order")
public record OrderItemResponse(

        @Schema(
                description = "Id of the product",
                example = "PROD-12345",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String productId,

        @Schema(
                description = "Quantity of the product",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int quantity,

        @Schema(
                description = "Unit price of the product",
                example = "10.50",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        BigDecimal price
) {

    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.productId(), item.quantity(), item.price());
    }
}
