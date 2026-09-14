---
title: Controladores REST
sidebar_position: 2
---

# Controladores REST

Un controlador REST expone operaciones HTTP como métodos Java normales. Spring se encarga de convertir la petición HTTP en parámetros del método, y el valor que devuelve el método en la respuesta HTTP.

## `@RestController` y las anotaciones de mapeo

```java
@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks")
    public List<Task> findAll() {
        return taskService.findAll();
    }

    @GetMapping("/tasks/{id}")
    public Task findById(@PathVariable Long id) {
        return taskService.findById(id);
    }
}
```

- `@RestController` combina `@Controller` (es un bean gestionado por Spring) con `@ResponseBody` (lo que devuelva cada método se serializa directamente al cuerpo de la respuesta, en JSON por defecto).
- `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` mapean el método HTTP y la ruta. `{id}` en la ruta es una variable de plantilla, capturada con `@PathVariable`.

## Leer el cuerpo de la petición

Para los métodos que reciben datos (crear, actualizar), el cuerpo JSON de la petición se convierte automáticamente en un objeto Java con `@RequestBody`:

```java
@PostMapping("/tasks")
public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest request) {
    Task created = taskService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

(El `@Valid` se explica en la página de [Validación](./validacion) — aquí basta con saber que activa la validación automática de `request` antes de que el método se ejecute.)

## Controlar el código de estado con `ResponseEntity`

Cuando el código de estado por defecto (200 OK) no es el que quieres, envuelve la respuesta en un `ResponseEntity`:

```java
@DeleteMapping("/tasks/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    taskService.delete(id);
    return ResponseEntity.noContent().build();
}
```

`ResponseEntity.noContent().build()` devuelve **204 No Content**, el código estándar para "operación completada, sin cuerpo que devolver". `ResponseEntity.status(HttpStatus.CREATED)` (en el ejemplo de crear) devuelve **201 Created**.

El controlador completo, con los cinco endpoints (`POST`, `GET` lista, `GET` por id, `PUT`, `DELETE`), está en [`TaskController.java`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/TaskController.java).
