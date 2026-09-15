package dev.springbootdocs.examples.tasks;

import java.util.List;

public interface TaskService {

    Task create(TaskRequest request, User currentUser);

    List<Task> findAll(User currentUser);

    Task findById(Long id, User currentUser);

    Task update(Long id, TaskRequest request, User currentUser);

    void delete(Long id, User currentUser);
}
