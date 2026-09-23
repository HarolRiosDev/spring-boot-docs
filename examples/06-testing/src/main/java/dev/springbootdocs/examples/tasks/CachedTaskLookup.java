package dev.springbootdocs.examples.tasks;

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
     * que devuelve este metodo es exactamente lo que se guarda en Redis, asi que su
     * {@code user} tiene que ser un {@link User} real y ya cargado, nunca un proxy perezoso.
     */
    @Cacheable(value = "tasks", key = "#id")
    public Task findById(Long id) {
        return taskRepository.findByIdWithUser(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
