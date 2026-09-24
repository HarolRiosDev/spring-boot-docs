package dev.springbootdocs.examples.tasks.controller;

import dev.springbootdocs.examples.tasks.dto.NotificationResponse;
import dev.springbootdocs.examples.tasks.security.UserPrincipal;
import dev.springbootdocs.examples.tasks.service.NotificationService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public List<NotificationResponse> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.findAll(principal.getUser());
    }
}
