package dev.springbootdocs.examples.tasks;

import java.util.List;

public interface TaskService {

    TaskResponse create(TaskRequest request, User currentUser);

    List<TaskResponse> findAll(User currentUser);

    TaskResponse findById(Long id, User currentUser);

    TaskResponse update(Long id, TaskRequest request, User currentUser);

    void delete(Long id, User currentUser);
}
