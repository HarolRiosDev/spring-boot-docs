package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.exception.TaskAccessDeniedException;
import dev.springbootdocs.examples.tasks.model.Role;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.model.User;
import dev.springbootdocs.examples.tasks.repository.TaskRepository;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final CachedTaskLookup cachedTaskLookup;

    public TaskServiceImpl(TaskRepository taskRepository, CachedTaskLookup cachedTaskLookup) {
        this.taskRepository = taskRepository;
        this.cachedTaskLookup = cachedTaskLookup;
    }

    @Override
    @Transactional
    public TaskResponse create(TaskRequest request, User currentUser) {
        Task task = new Task(request.titulo(), request.descripcion(), request.completada(), currentUser);
        return TaskResponse.from(taskRepository.save(task));
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
     * Looks up the task through the cache (CachedTaskLookup — a hit or a miss is
     * transparent from here), then ALWAYS enforces the ownership/admin check on every
     * call. A cache hit never skips this: the cache only remembers "what is task #id",
     * never "who is allowed to see it" — that decision depends on who is asking right
     * now, so it must run on every call regardless of where the Task came from. Returns
     * the mutable entity for callers (update/delete) that need to modify or remove it.
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
