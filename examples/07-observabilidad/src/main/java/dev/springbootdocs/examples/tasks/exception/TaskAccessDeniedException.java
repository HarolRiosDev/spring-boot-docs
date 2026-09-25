package dev.springbootdocs.examples.tasks.exception;

public class TaskAccessDeniedException extends RuntimeException {

    public TaskAccessDeniedException(Long id) {
        super("No tienes permiso para acceder a la tarea con id " + id);
    }
}
