---
title: 'Capas: controller → service → repository'
sidebar_position: 4
---

# Capas: controller → service → repository

Separar el código en capas evita que una sola clase mezcle responsabilidades distintas (HTTP, reglas de negocio, acceso a datos). Cada capa solo habla con la capa inmediatamente inferior, y lo hace a través de una interfaz.

## Las tres capas del ejemplo

```
TaskController  (capa web: recibe HTTP, valida, devuelve HTTP)
      ↓ usa
TaskService     (capa de negocio: reglas — "si no existe, lanzar TaskNotFoundException")
      ↓ usa
TaskRepository  (capa de datos: guardar/leer/borrar — hoy en memoria, mañana en una BBDD real)
```

**Controller** — solo se ocupa de HTTP: mapear rutas, leer el cuerpo de la petición, devolver el código de estado correcto. No contiene lógica de negocio.

```java
@GetMapping("/tasks/{id}")
public Task findById(@PathVariable Long id) {
    return taskService.findById(id);
}
```

**Service** — contiene las reglas de negocio. Por ejemplo, decidir qué pasa cuando pides una tarea que no existe:

```java
@Override
public Task findById(Long id) {
    return taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
}
```

El controller no sabe nada de esta decisión — solo delega en el service y confía en que, si algo va mal, se lanzará una excepción (ver [Manejo de errores](./manejo-errores)).

**Repository** — solo sabe guardar y recuperar datos. No sabe nada de HTTP ni de reglas de negocio:

```java
public interface TaskRepository {
    Task save(Task task);
    List<Task> findAll();
    Optional<Task> findById(Long id);
    void deleteById(Long id);
}
```

## Por qué importa esta separación

Cada capa se puede razonar, cambiar y (más adelante, en la Fase 6) testear de forma aislada:

- Puedes cambiar cómo se guardan las tareas (de memoria a una base de datos real, en la Fase 2) sin tocar el `TaskService` ni el `TaskController` — solo cambia la implementación de `TaskRepository`.
- Puedes cambiar una regla de negocio (por ejemplo, qué pasa si intentas borrar una tarea ya completada) sin tocar cómo se exponen los endpoints.
- El controller queda pequeño y fácil de leer: es solo el "traductor" entre HTTP y las operaciones del service.

Este es exactamente el mismo patrón que verás en el resto del roadmap — cada fase nueva añade capas o las reemplaza (por ejemplo, la Fase 2 sustituye `InMemoryTaskRepository` por una implementación con Spring Data JPA), pero el `TaskController` y el `TaskService` apenas cambian.
