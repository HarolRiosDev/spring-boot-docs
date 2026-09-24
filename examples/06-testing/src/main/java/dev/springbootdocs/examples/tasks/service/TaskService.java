package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.model.User;
import java.util.List;

public interface TaskService {

    TaskResponse create(TaskRequest request, User currentUser);

    List<TaskResponse> findAll(User currentUser);

    TaskResponse findById(Long id, User currentUser);

    TaskResponse update(Long id, TaskRequest request, User currentUser);

    void delete(Long id, User currentUser);
}
