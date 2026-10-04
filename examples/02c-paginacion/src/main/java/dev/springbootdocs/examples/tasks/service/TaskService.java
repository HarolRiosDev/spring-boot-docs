package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskService {

    Task create(TaskRequest request);

    Page<Task> findAll(Pageable pageable);

    Task findById(Long id);

    Task update(Long id, TaskRequest request);

    void delete(Long id);
}
