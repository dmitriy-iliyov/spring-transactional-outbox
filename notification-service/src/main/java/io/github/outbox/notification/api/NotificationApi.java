package io.github.outbox.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * The OpenAPI document of the notifications API, as the order service documents its own in {@code OrderApi}: the
 * springdoc annotations sit on this interface, the controller only carries the Spring MVC annotations.
 */
public interface NotificationApi {

    @Operation(summary = "Get notifications by order id")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notification list successfully retrieved",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class))
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Request validation failed"),
            @ApiResponse(
                    responseCode = "500",
                    description = "Notification retrieval failed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = Problem.class)
                    )
            )
    })
    List<NotificationResponse> findByOrderId(
            @Parameter(
                    description = "Id of the order whose notifications to list",
                    required = true,
                    example = "1"
            )
            @Positive(message = "orderId must be positive") Long orderId
    );
}
