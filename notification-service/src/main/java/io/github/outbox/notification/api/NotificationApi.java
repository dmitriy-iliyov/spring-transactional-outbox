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
 * The notifications API contract, shared by request validation and OpenAPI documentation.
 */
public interface NotificationApi {

    @Operation(summary = "Get notification by id")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notification successfully retrieved",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Request validation failed"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    NotificationResponse findById(@Positive(message = "id must be positive") Long id);

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
                    description = "Internal server error",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = Problem.class)
                    )
            )
    })
    List<NotificationResponse> findByOrderId(
            @Parameter(description = "Id of the order to find notifications for", required = true, example = "1")
            @Positive(message = "orderId must be positive") Long orderId
    );
}
