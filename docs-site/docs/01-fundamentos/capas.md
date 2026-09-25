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

## Un paquete por capa

Las capas no son solo una idea: en los ejemplos, cada una vive en su propio paquete. Así queda `examples/01-fundamentos`:

```
dev.springbootdocs.examples.tasks
├── TasksApplication.java         (arranque: @SpringBootApplication)
├── controller/   TaskController
├── service/      TaskService, TaskServiceImpl
├── repository/   TaskRepository, InMemoryTaskRepository
├── model/        Task
├── dto/          TaskRequest
└── exception/    GlobalExceptionHandler, ApiError, ValidationApiError, TaskNotFoundException
```

- `model/` guarda los objetos del dominio (más adelante, las entidades JPA). `dto/` guarda los objetos que entran y salen por HTTP, que no siempre coinciden con el modelo.
- `exception/` junta las excepciones propias y el manejador global que las traduce a respuestas HTTP (ver [Manejo de errores](./manejo-errores)).
- Las fases siguientes añaden paquetes con el mismo criterio: `security/` en la Fase 3, `config/` en la Fase 4, `messaging/` en la Fase 5 y `observability/` en la Fase 7.

La clase `@SpringBootApplication` se queda en el paquete raíz a propósito: Spring busca componentes (`@Service`, `@RestController`, entidades, repositorios...) en el paquete de esa clase **y en todos sus subpaquetes**. Si la movieras dentro de `controller/`, Spring dejaría de encontrar todo lo que está en `service/`, `repository/`, etc.

Los tests siguen la misma estructura: `TaskControllerTest` vive en el paquete `controller` de `src/test/java`, al lado (en espejo) de la clase que prueba.

Organizar por capa no es la única opción. En proyectos grandes también es muy común organizar **por funcionalidad** (`task/`, `user/`, `auth/`...), con el controller, el service y el repository de cada funcionalidad juntos en su paquete. Aquí usamos capas porque hacen visible, en la propia estructura de carpetas, lo que enseña esta página.
