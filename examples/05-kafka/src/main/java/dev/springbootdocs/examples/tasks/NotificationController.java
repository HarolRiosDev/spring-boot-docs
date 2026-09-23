package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/notifications")
    public List<NotificationResponse> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        User currentUser = principal.getUser();
        List<Notification> notifications = currentUser.getRole() == Role.ADMIN
                ? notificationRepository.findAll()
                : notificationRepository.findByUser(currentUser);
        return notifications.stream().map(NotificationResponse::from).toList();
    }
}
