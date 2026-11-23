package io.github.outbox.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * API for retrieving notifications related to orders.
 */
@RequestMapping("/api/v1/notifications")
public interface NotificationApi {

    @Operation(
            summary = "Get notifications by order id",
            description = "Retrieves all notifications associated with a specific order"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notification list successfully retrieved",
                    content = @Content(
                            array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                                    schema = @Schema(implementation = NotificationResponse.class)
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @GetMapping
    List<NotificationResponse> findByOrderId(
            @Parameter(
                    description = "ID of the order to retrieve notifications for",
                    required = true,
                    example = "1"
            )
            @RequestParam("orderId") @Positive(message = "orderId must be positive") Long orderId
    );
}
