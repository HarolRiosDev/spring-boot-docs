package dev.springbootdocs.examples.tasks;

import java.time.Instant;

public record NotificationResponse(Long id, Long taskId, TaskEventType eventType, String mensaje, Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTaskId(),
                notification.getEventType(),
                notification.getMensaje(),
                notification.getCreatedAt());
    }
}
