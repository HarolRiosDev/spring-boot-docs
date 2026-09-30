package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.dto.TaskUpdateRequest;
import dev.springbootdocs.examples.tasks.exception.TaskNotFoundException;
import dev.springbootdocs.examples.tasks.exception.TaskVersionConflictException;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.repository.TaskCommentRepository;
import dev.springbootdocs.examples.tasks.repository.TaskRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;

    public TaskServiceImpl(TaskRepository taskRepository, TaskCommentRepository taskCommentRepository) {
        this.taskRepository = taskRepository;
        this.taskCommentRepository = taskCommentRepository;
    }

    @Override
    @Transactional
    public TaskResponse create(TaskRequest request) {
        Task task = new Task(request.titulo(), request.descripcion(), request.completada());
        // saveAndFlush: el INSERT se ejecuta ya, y con él llegan los valores que genera la BD (FH_ALTA)
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> findAll() {
        return taskRepository.findAll(Sort.by("id")).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        return TaskResponse.from(getTask(id));
    }

    @Override
    @Transactional
    public TaskResponse update(Long id, TaskUpdateRequest request) {
        Task task = getTask(id);
        if (!task.getVersion().equals(request.version())) {
            throw new TaskVersionConflictException(id, request.version(), task.getVersion());
        }
        task.setTitulo(request.titulo());
        task.setDescripcion(request.descripcion());
        task.setCompletada(request.completada());
        // flush: el UPDATE se ejecuta ya, y Hibernate relee FH_MODIFICACION (la pone el trigger)
        taskRepository.flush();
        return TaskResponse.from(task);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Task task = getTask(id);
        // La clave foránea no tiene ON DELETE CASCADE: primero los comentarios
        taskCommentRepository.deleteByTaskId(id);
        taskRepository.delete(task);
    }

    private Task getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
