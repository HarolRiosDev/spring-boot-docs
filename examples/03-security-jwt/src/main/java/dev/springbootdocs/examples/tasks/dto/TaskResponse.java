package dev.springbootdocs.examples.tasks.dto;

import dev.springbootdocs.examples.tasks.model.Task;

public record TaskResponse(Long id, String titulo, String descripcion, boolean completada, String username) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitulo(),
                task.getDescripcion(),
                task.isCompletada(),
                task.getUser().getUsername());
    }
}
