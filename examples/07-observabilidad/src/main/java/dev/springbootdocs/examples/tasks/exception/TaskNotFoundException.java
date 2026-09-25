package dev.springbootdocs.examples.tasks.exception;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("No existe ninguna tarea con id " + id);
    }
}
