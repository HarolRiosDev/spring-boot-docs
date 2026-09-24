package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.exception.TaskAccessDeniedException;
import dev.springbootdocs.examples.tasks.messaging.TaskEvent;
import dev.springbootdocs.examples.tasks.messaging.TaskEventType;
import dev.springbootdocs.examples.tasks.model.Role;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.model.User;
import dev.springbootdocs.examples.tasks.repository.TaskRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final CachedTaskLookup cachedTaskLookup;
    private final ApplicationEventPublisher eventPublisher;

    public TaskServiceImpl(
            TaskRepository taskRepository,
            CachedTaskLookup cachedTaskLookup,
            ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.cachedTaskLookup = cachedTaskLookup;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public TaskResponse create(TaskRequest request, User currentUser) {
        Task task = new Task(request.titulo(), request.descripcion(), request.completada(), currentUser);
        Task saved = taskRepository.save(task);
        eventPublisher.publishEvent(new TaskEvent(
                saved.getId(), TaskEventType.CREATED, saved.getTitulo(), currentUser.getUsername(), Instant.now()));
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
        boolean wasCompleted = task.isCompletada();
        task.setTitulo(request.titulo());
        task.setDescripcion(request.descripcion());
        task.setCompletada(request.completada());
        Task saved = taskRepository.save(task);
        if (!wasCompleted && saved.isCompletada()) {
            eventPublisher.publishEvent(new TaskEvent(
                    saved.getId(), TaskEventType.COMPLETED, saved.getTitulo(), saved.getUser().getUsername(), Instant.now()));
        }
        return TaskResponse.from(saved);
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
