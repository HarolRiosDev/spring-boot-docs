package dev.springbootdocs.examples.tasks;

import java.time.Instant;

/**
 * El mensaje real que viaja por el topic de Kafka "task-events" — el único tipo que se
 * serializa. {@code ownerUsername} es siempre el dueño de la tarea (task.getUser()), nunca
 * quien hizo la petición HTTP: un ADMIN puede completar la tarea de otro usuario, y la
 * notificación debe llegar a ese otro usuario, no al admin.
 */
public record TaskEvent(Long taskId, TaskEventType eventType, String titulo, String ownerUsername, Instant timestamp) {
}
