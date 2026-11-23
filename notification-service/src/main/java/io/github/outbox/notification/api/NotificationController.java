package io.github.outbox.notification.api;

import io.github.outbox.notification.notification.NotificationService;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService service;

    @GetMapping
    public List<NotificationResponse> findByOrderId(
            @RequestParam("orderId") @Positive(message = "orderId must be positive") Long orderId
    ) {
        return service.findByOrderId(orderId);
    }
}
