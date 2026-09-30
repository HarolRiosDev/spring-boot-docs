package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.dto.TaskUpdateRequest;
import java.util.List;

public interface TaskService {

    TaskResponse create(TaskRequest request);

    List<TaskResponse> findAll();

    TaskResponse findById(Long id);

    TaskResponse update(Long id, TaskUpdateRequest request);

    void delete(Long id);

    int purgeCompleted(int days);
}
