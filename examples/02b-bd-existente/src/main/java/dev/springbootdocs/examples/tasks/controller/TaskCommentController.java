package dev.springbootdocs.examples.tasks.controller;

import dev.springbootdocs.examples.tasks.dto.CommentRequest;
import dev.springbootdocs.examples.tasks.dto.CommentResponse;
import dev.springbootdocs.examples.tasks.service.TaskCommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskCommentController {

    private final TaskCommentService taskCommentService;

    public TaskCommentController(TaskCommentService taskCommentService) {
        this.taskCommentService = taskCommentService;
    }

    @GetMapping("/tasks/{taskId}/comments")
    public List<CommentResponse> findByTask(@PathVariable Long taskId) {
        return taskCommentService.findByTask(taskId);
    }

    @PostMapping("/tasks/{taskId}/comments")
    public ResponseEntity<CommentResponse> add(@PathVariable Long taskId, @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskCommentService.add(taskId, request));
    }
}
