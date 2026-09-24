package dev.springbootdocs.examples.tasks.messaging;

import dev.springbootdocs.examples.tasks.model.Notification;
import dev.springbootdocs.examples.tasks.repository.NotificationRepository;
import dev.springbootdocs.examples.tasks.repository.UserRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskEventListener {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public TaskEventListener(UserRepository userRepository, NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @KafkaListener(topics = "task-events")
    public void onTaskEvent(TaskEvent event) {
        userRepository.findByUsername(event.ownerUsername()).ifPresent(owner -> {
            String mensaje = switch (event.eventType()) {
                case CREATED -> "Se creó la tarea \"" + event.titulo() + "\"";
                case COMPLETED -> "Se completó la tarea \"" + event.titulo() + "\"";
            };
            notificationRepository.save(new Notification(event.taskId(), event.eventType(), mensaje, owner));
        });
    }
}
