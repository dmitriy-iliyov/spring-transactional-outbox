package io.github.outbox.notification.api;

import io.github.outbox.notification.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

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
    @GetMapping("/{id}")
    public NotificationResponse findById(
            @PathVariable("id") @Positive(message = "id must be positive") Long id
    ) {
        return service.findById(id);
    }

    @Operation(summary = "Get notifications by order id")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Notification list successfully retrieved",
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class))
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Request validation failed")
    })
    @GetMapping
    public List<NotificationResponse> findByOrderId(
            @RequestParam("orderId") @Positive(message = "orderId must be positive") Long orderId
    ) {
        return service.findByOrderId(orderId);
    }
}
