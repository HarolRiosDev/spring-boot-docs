package dev.springbootdocs.examples.tasks.controller;

import dev.springbootdocs.examples.tasks.dto.PurgeResponse;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.dto.TaskUpdateRequest;
import dev.springbootdocs.examples.tasks.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(request));
    }

    @GetMapping("/tasks")
    public List<TaskResponse> findAll() {
        return taskService.findAll();
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse findById(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @PutMapping("/tasks/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks/purge")
    public PurgeResponse purge(@RequestParam @Min(1) int days) {
        return new PurgeResponse(taskService.purgeCompleted(days));
    }
}
