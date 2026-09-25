package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.exception.TaskAccessDeniedException;
import dev.springbootdocs.examples.tasks.model.Role;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.model.User;
import dev.springbootdocs.examples.tasks.repository.TaskRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final CachedTaskLookup cachedTaskLookup;
    private final Counter taskCreations;

    public TaskServiceImpl(
            TaskRepository taskRepository, CachedTaskLookup cachedTaskLookup, MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.cachedTaskLookup = cachedTaskLookup;
        // se registra al arrancar: la serie existe (a 0) antes de la primera tarea
        this.taskCreations = Counter.builder("tasks.creations")
                .description("Tareas creadas desde que arrancó la aplicación")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public TaskResponse create(TaskRequest request, User currentUser) {
        Task task = new Task(request.titulo(), request.descripcion(), request.completada(), currentUser);
        Task saved = taskRepository.save(task);
        // dentro de la transacción: si el commit fallara después, contaría una tarea que no
        // llegó a existir. Para una métrica es aceptable.
        taskCreations.increment();
        return TaskResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> findAll(User currentUser) {
        List<Task> tasks = currentUser.getRole() == Role.ADMIN
                ? taskRepository.findAll()
                : taskRepository.findByUser(currentUser);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse findById(Long id, User currentUser) {
        return TaskResponse.from(getTaskForCurrentUser(id, currentUser));
    }

    @Override
    @Transactional
    @CacheEvict(value = "tasks", key = "#id")
    public TaskResponse update(Long id, TaskRequest request, User currentUser) {
        Task task = getTaskForCurrentUser(id, currentUser);
        task.setTitulo(request.titulo());
        task.setDescripcion(request.descripcion());
        task.setCompletada(request.completada());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Override
    @Transactional
    @CacheEvict(value = "tasks", key = "#id")
    public void delete(Long id, User currentUser) {
        Task task = getTaskForCurrentUser(id, currentUser);
        taskRepository.delete(task);
    }

    /**
     * Busca la tarea a través de la caché (CachedTaskLookup: desde aquí un acierto y un
     * fallo son indistinguibles) y SIEMPRE aplica la comprobación de dueño/admin en cada
     * llamada. Un acierto de caché nunca se la salta: la caché solo recuerda "qué es la
     * tarea #id", nunca "quién puede verla", porque eso depende de quién pregunta en este
     * momento. Devuelve la entidad mutable para quien la necesita modificar o borrar
     * (update/delete).
     */
    private Task getTaskForCurrentUser(Long id, User currentUser) {
        Task task = cachedTaskLookup.findById(id);
        requireAccess(task, currentUser);
        return task;
    }

    private void requireAccess(Task task, User currentUser) {
        boolean isOwner = task.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new TaskAccessDeniedException(task.getId());
        }
    }
}
