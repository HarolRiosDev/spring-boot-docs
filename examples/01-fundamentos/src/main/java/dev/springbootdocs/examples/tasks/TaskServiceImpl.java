package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public Task create(TaskRequest request) {
        Task task = new Task(null, request.titulo(), request.descripcion(), request.completada());
        return taskRepository.save(task);
    }

    @Override
    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    @Override
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Override
    public Task update(Long id, TaskRequest request) {
        findById(id);
        Task updated = new Task(id, request.titulo(), request.descripcion(), request.completada());
        return taskRepository.save(updated);
    }

    @Override
    public void delete(Long id) {
        findById(id);
        taskRepository.deleteById(id);
    }
}
