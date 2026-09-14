package dev.springbootdocs.examples.tasks;

import java.util.List;

public interface TaskService {

    Task create(TaskRequest request);

    List<Task> findAll();

    Task findById(Long id);

    Task update(Long id, TaskRequest request);

    void delete(Long id);
}
