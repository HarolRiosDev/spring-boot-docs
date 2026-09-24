package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> findAll(User currentUser) {
        List<Notification> notifications = currentUser.getRole() == Role.ADMIN
                ? notificationRepository.findAll()
                : notificationRepository.findByUser(currentUser);
        return notifications.stream().map(NotificationResponse::from).toList();
    }
}
