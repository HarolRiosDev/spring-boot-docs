---
title: Colecciones
sidebar_position: 2
---

# Colecciones

Las tres que vas a usar constantemente en este sitio: `List`, `Set` y `Map` — todas interfaces, con varias implementaciones cada una.

## `List`

Elementos ordenados, se permiten duplicados:

```java
List<Task> tareas = new ArrayList<>();
tareas.add(new Task("Comprar leche"));
tareas.add(new Task("Comprar leche")); // duplicado permitido

Task primera = tareas.get(0);
```

`ArrayList` es la implementación que verás casi siempre — respaldada por un array que crece automáticamente.

## `Set`

Sin duplicados, sin orden garantizado (salvo que uses `LinkedHashSet`, que preserva el orden de inserción):

```java
Set<String> roles = new HashSet<>();
roles.add("ADMIN");
roles.add("ADMIN"); // no pasa nada: ya estaba, el Set sigue teniendo 1 elemento
```

## `Map`

Pares clave-valor, sin claves duplicadas:

```java
Map<Long, Task> cache = new HashMap<>();
cache.put(1L, tarea);
Task encontrada = cache.get(1L); // null si la clave no existe
```

`HashMap` es la implementación estándar. Esto es, de hecho, la idea conceptual detrás de la caché de la [Fase 4](/docs/04-cache-redis) — `ConcurrentMapCacheManager` (el que usan los tests) literalmente guarda cada entrada en un `Map`.

## Programa contra la interfaz, no la implementación

Igual que con `TaskRepository` en la página anterior, declara el tipo como la interfaz, no como la clase concreta:

```java
// Así:
List<Task> tareas = new ArrayList<>();

// No así:
ArrayList<Task> tareas = new ArrayList<>();
```

La diferencia importa: con `List<Task>` puedes cambiar la implementación real (`ArrayList` → `LinkedList`, por ejemplo) sin tocar el resto del código que usa `tareas` — el mismo principio que hace que `TaskController` no sepa (ni le importe) si, por debajo, `TaskService` está respaldado por una lista en memoria o por Postgres.

## Listas inmutables

`List.of(...)`, `Set.of(...)` y `Map.of(...)` crean colecciones que no se pueden modificar después — un `add()` sobre una de estas lanza `UnsupportedOperationException`:

```java
List<String> roles = List.of("USER", "ADMIN");
roles.add("GUEST"); // lanza UnsupportedOperationException
```

Útil para valores que no deberían cambiar nunca después de crearse — el mismo espíritu que un `record` (como `TaskResponse` en los ejemplos ejecutables): una vez construido, no se puede mutar.
