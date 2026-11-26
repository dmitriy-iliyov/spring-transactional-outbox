package io.github.outbox.notification.api;

import io.github.outbox.notification.notification.NotificationService;
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
public class NotificationController implements NotificationApi {

    private final NotificationService service;

    @GetMapping("/{id}")
    @Override
    public NotificationResponse findById(
            @PathVariable("id") Long id
    ) {
        return service.findById(id);
    }

    @GetMapping
    @Override
    public List<NotificationResponse> findByOrderId(
            @RequestParam("orderId") Long orderId
    ) {
        return service.findByOrderId(orderId);
    }
}
