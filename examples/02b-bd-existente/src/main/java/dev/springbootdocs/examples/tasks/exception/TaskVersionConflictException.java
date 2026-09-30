package dev.springbootdocs.examples.tasks.exception;

public class TaskVersionConflictException extends RuntimeException {

    public TaskVersionConflictException(Long id, Integer expected, Integer actual) {
        super("La tarea " + id + " cambió desde que la leíste (leíste la versión " + expected
                + ", la actual es la " + actual + "); vuelve a leerla antes de modificarla");
    }
}
