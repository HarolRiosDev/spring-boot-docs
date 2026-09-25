package dev.springbootdocs.examples.tasks.controller;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.security.UserPrincipal;
import dev.springbootdocs.examples.tasks.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Tareas", description = "CRUD de tareas: cada usuario ve las suyas; un ADMIN, todas")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/tasks")
    @Operation(summary = "Crear una tarea")
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponse created = taskService.create(request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/tasks")
    @Operation(summary = "Listar las tareas visibles para el usuario")
    public List<TaskResponse> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        return taskService.findAll(principal.getUser());
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Obtener una tarea por id")
    public TaskResponse findById(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.findById(id, principal.getUser());
    }

    @PutMapping("/tasks/{id}")
    @Operation(summary = "Actualizar una tarea")
    public TaskResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.update(id, request, principal.getUser());
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "Borrar una tarea")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        taskService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
