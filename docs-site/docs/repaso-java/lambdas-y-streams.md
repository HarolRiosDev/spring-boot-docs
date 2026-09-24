---
title: Lambdas y streams
sidebar_position: 3
---

# Lambdas y streams

Esto es lo que más vas a reconocer en el código real de este sitio — casi todos los ejemplos ejecutables tienen al menos una línea con `.stream()`.

## Interfaces funcionales

Una interfaz funcional tiene **un único método abstracto** — eso es lo que permite reemplazar una implementación completa por una lambda. Las más comunes ya vienen en Java (`java.util.function`):

- `Function<T, R>` — recibe un `T`, devuelve un `R`.
- `Supplier<T>` — no recibe nada, devuelve un `T`.
- `Predicate<T>` — recibe un `T`, devuelve `boolean`.
- `Runnable` — no recibe nada, no devuelve nada.

## Sintaxis lambda

Una lambda es una implementación corta de una interfaz funcional, sin escribir la clase entera:

```java
// Con clase anónima (como se hacía antes de Java 8):
Comparator<Task> porTitulo = new Comparator<Task>() {
    @Override
    public int compare(Task a, Task b) {
        return a.getTitulo().compareTo(b.getTitulo());
    }
};

// Con lambda (equivalente, mucho más corto):
Comparator<Task> porTitulo = (a, b) -> a.getTitulo().compareTo(b.getTitulo());
```

`(a, b) -> ...` es la lambda: los parámetros a la izquierda de `->`, el cuerpo a la derecha. Si el cuerpo es una sola expresión (como aquí), no hace falta `return` ni llaves.

## Streams

Un stream procesa una colección en una cadena de pasos, sin bucles `for` explícitos:

```java
List<TaskResponse> completadas = tasks.stream()
        .filter(Task::isCompletada)
        .map(TaskResponse::from)
        .toList();
```

- `.filter(...)` — se queda solo con los elementos que cumplen una condición.
- `.map(...)` — transforma cada elemento en otra cosa (aquí, de `Task` a `TaskResponse`).
- `.toList()` — vuelca el resultado a una `List` normal.

Esta línea exacta —`tasks.stream().map(TaskResponse::from).toList()`— aparece, sin cambios, en `TaskServiceImpl.findAll()` de las Fases 3 a 6 (desde la Fase 3 las tareas tienen un dueño, y `TaskResponse` existe precisamente para no exponer la entidad `User` completa): es como se convierte una lista de entidades (`Task`) en una lista de DTOs de salida (`TaskResponse`), sin escribir un `for` que las recorra a mano.

## Method references (`::`)

`Task::isCompletada` es un atajo para la lambda `task -> task.isCompletada()` — cuando la lambda solo llama a un método existente sobre su parámetro, `::` es más corto y (para la mayoría) más legible que escribir la lambda entera. Lo mismo con `TaskResponse::from`, que reemplaza a `task -> TaskResponse.from(task)`.

No hace falta memorizar todas las formas de `::` que existen (hay varias: método de instancia, método estático, constructor...) — con reconocer que `Clase::metodo` es "llama a este método" es suficiente para leer el código de los ejemplos.
