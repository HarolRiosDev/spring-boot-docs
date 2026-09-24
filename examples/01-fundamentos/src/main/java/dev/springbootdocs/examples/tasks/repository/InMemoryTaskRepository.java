package dev.springbootdocs.examples.tasks.repository;

import dev.springbootdocs.examples.tasks.model.Task;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTaskRepository implements TaskRepository {

    private final ConcurrentHashMap<Long, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Task save(Task task) {
        Long id = task.id() != null ? task.id() : nextId.getAndIncrement();
        Task saved = new Task(id, task.titulo(), task.descripcion(), task.completada());
        tasks.put(id, saved);
        return saved;
    }

    @Override
    public List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    @Override
    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public void deleteById(Long id) {
        tasks.remove(id);
    }
}
