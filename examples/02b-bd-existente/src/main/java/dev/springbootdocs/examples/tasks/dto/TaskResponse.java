package dev.springbootdocs.examples.tasks.dto;

import dev.springbootdocs.examples.tasks.model.Task;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String titulo,
        String descripcion,
        boolean completada,
        LocalDateTime fechaAlta,
        LocalDateTime fechaModificacion,
        Integer version) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitulo(), task.getDescripcion(), task.isCompletada(),
                task.getFechaAlta(), task.getFechaModificacion(), task.getVersion());
    }
}
