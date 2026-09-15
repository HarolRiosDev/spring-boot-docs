package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
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
    public TaskResponse update(Long id, TaskRequest request, User currentUser) {
        Task task = getTaskForCurrentUser(id, currentUser);
        task.setTitulo(request.titulo());
        task.setDescripcion(request.descripcion());
        task.setCompletada(request.completada());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Override
    @Transactional
    public void delete(Long id, User currentUser) {
        Task task = getTaskForCurrentUser(id, currentUser);
        taskRepository.delete(task);
    }

    /**
     * Looks up the task and enforces the ownership/admin check, returning the mutable
     * entity for callers (update/delete) that need to modify or remove it. The public
     * findById maps this to a TaskResponse instead of exposing the entity directly.
     */
    private Task getTaskForCurrentUser(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
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
