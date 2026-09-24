package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.model.Task;
import java.util.List;

public interface TaskService {

    Task create(TaskRequest request);

    List<Task> findAll();

    Task findById(Long id);

    Task update(Long id, TaskRequest request);

    void delete(Long id);
}
