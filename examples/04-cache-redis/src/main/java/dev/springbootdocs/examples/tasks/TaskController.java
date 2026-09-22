package dev.springbootdocs.examples.tasks;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponse created = taskService.create(request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/tasks")
    public List<TaskResponse> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        return taskService.findAll(principal.getUser());
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse findById(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.findById(id, principal.getUser());
    }

    @PutMapping("/tasks/{id}")
    public TaskResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.update(id, request, principal.getUser());
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        taskService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
