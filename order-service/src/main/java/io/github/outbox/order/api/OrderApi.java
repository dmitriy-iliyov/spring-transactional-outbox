package io.github.outbox.order.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;

public interface OrderApi {

    @Operation(summary = "Create order")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Order successfully created",
                    headers = @Header(name = "Location", description = "URI of the created order"),
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Request validation failed"),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many requests, rate limit exceeded",
                    headers = @Header(name = "Retry-After", description = "Time in seconds to wait before retrying")
            )
    })
    ResponseEntity<OrderResponse> create(
            @Parameter(description = "Correlation id of the request, a new one is generated when absent")
            @Size(max = 128, message = "X-Correlation-Id must be at most {max} characters") String correlationId,
            @Valid CreateOrderRequest request
    );

    @Operation(summary = "Cancel order by id")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order successfully cancelled",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Request validation failed"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order is already cancelled")
    })
    OrderResponse cancel(
            @Parameter(description = "Correlation id of the request, a new one is generated when absent")
            @Size(max = 128, message = "X-Correlation-Id must be at most {max} characters") String correlationId,
            @Parameter(description = "Id of the order to cancel", required = true)
            @Positive(message = "id must be positive") Long id
    );

    @Operation(summary = "Get order by id")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order successfully retrieved",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Request validation failed"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    OrderResponse get(
            @Parameter(description = "Id of the order", required = true)
            @Positive(message = "id must be positive") Long id
    );
}
