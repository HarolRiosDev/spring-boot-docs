package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.exception.TaskNotFoundException;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.repository.TaskRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class CachedTaskLookup {

    private final TaskRepository taskRepository;

    public CachedTaskLookup(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * Usa {@code findByIdWithUser} (join fetch) y no el {@code findById} heredado: el valor
     * que devuelve este método es exactamente lo que se guarda en Redis, así que su
     * {@code user} tiene que ser un {@link dev.springbootdocs.examples.tasks.model.User User} real y ya cargado, nunca un proxy perezoso.
     */
    @Cacheable(value = "tasks", key = "#id")
    public Task findById(Long id) {
        return taskRepository.findByIdWithUser(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
