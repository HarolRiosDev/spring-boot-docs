package dev.springbootdocs.examples.tasks.controller;

import dev.springbootdocs.examples.tasks.dto.SliceResponse;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.SortDefault;
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
    public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest request) {
        Task created = taskService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Spring construye el Pageable con ?page=, ?size= y ?sort=. @SortDefault y no
    // @PageableDefault: este último fija size=10 aunque la configuración diga otra cosa
    @GetMapping("/tasks")
    public PagedModel<Task> findAll(
            @RequestParam(required = false) Boolean completada,
            @RequestParam(required = false) String q,
            @SortDefault(sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        // PagedModel da un JSON estable; un Page devuelto tal cual expone su estructura interna
        return new PagedModel<>(taskService.findAll(completada, q, pageable));
    }

    @GetMapping("/tasks/recientes")
    public SliceResponse<Task> findRecientes(Pageable pageable) {
        return SliceResponse.from(taskService.findRecientes(pageable));
    }

    @GetMapping("/tasks/{id}")
    public Task findById(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @PutMapping("/tasks/{id}")
    public Task update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
