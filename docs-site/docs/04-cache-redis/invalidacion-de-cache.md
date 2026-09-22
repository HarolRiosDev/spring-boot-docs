---
title: Invalidación de caché
sidebar_position: 3
---

# Invalidación de caché

Una caché sin invalidación es un bug esperando a pasar: en cuanto el dato subyacente cambia, cualquier lectura cacheada queda desactualizada hasta que expire por TTL (ver [Redis como backend](./redis-como-backend)). Esta fase invalida explícitamente en cada escritura, en vez de depender solo del tiempo.

## `@CacheEvict` en las escrituras

```java
@Override
@Transactional
@CacheEvict(value = "tasks", key = "#id")
public TaskResponse update(Long id, TaskRequest request, User currentUser) {
    Task task = getTaskForCurrentUser(id, currentUser);
    task.setTitulo(request.titulo());
    task.setDescripcion(request.descripcion());
    task.setCompletada(request.completada());
    return TaskResponse.from(taskRepository.save(task));
}

@Override
@Transactional
@CacheEvict(value = "tasks", key = "#id")
public void delete(Long id, User currentUser) {
    Task task = getTaskForCurrentUser(id, currentUser);
    taskRepository.delete(task);
}
```

Mismo `value`/`key` que el `@Cacheable` de `CachedTaskLookup` (ver [Spring Cache básico](./spring-cache-basico)) — así invalidan exactamente la entrada que ese id tenía cacheada. `create` no lleva `@CacheEvict`: no hay nada cacheado todavía para un id que acaba de nacer.

## Por qué el orden de las comprobaciones importa

`@CacheEvict` solo se dispara, por defecto, si el método anotado termina **sin lanzar excepción** — es el valor por defecto de su atributo `beforeInvocation` (`false`). `update`/`delete` llaman primero a `getTaskForCurrentUser`, que comprueba ownership y lanza `TaskAccessDeniedException`/`TaskNotFoundException` **antes** de tocar la base de datos si el acceso no es válido:

```java
private Task getTaskForCurrentUser(Long id, User currentUser) {
    Task task = cachedTaskLookup.findById(id);
    requireAccess(task, currentUser);
    return task;
}
```

Un intento de `update`/`delete` que falla por falta de permiso nunca llega a invalidar la caché — y es el comportamiento correcto: no hubo ninguna escritura real que invalidar. No hace falta fijar `beforeInvocation` a mano; el valor por defecto de Spring ya es el que se necesita aquí, siempre que la comprobación de acceso ocurra antes de la escritura (no después).

Un matiz honesto: `beforeInvocation=false` garantiza que la invalidación ocurre después de que el método termine bien, pero **no** garantiza que ocurra después del *commit* de la transacción. El interceptor de caché y el de transacciones tienen ambos precedencia `LOWEST_PRECEDENCE` por defecto, así que su orden relativo no es determinista salvo que se fije explícitamente con `@Order`. En un ejemplo de instancia única como este da igual; en un escenario con lectores concurrentes, una lectura que caiga en esa ventana podría volver a cachear el valor viejo.

## La comprobación de ownership nunca se salta, ni en un acierto de caché

Esto es lo más importante de toda la fase, y por eso tiene su propio test dedicado. La caché memoriza **qué es la tarea con id X**, nunca **quién puede verla** — esa segunda pregunta depende de quién está preguntando en este momento, así que tiene que evaluarse en cada llamada, sin excepción:

```java
private Task getTaskForCurrentUser(Long id, User currentUser) {
    Task task = cachedTaskLookup.findById(id); // puede venir de caché o de la BD — es indistinguible desde aquí
    requireAccess(task, currentUser);          // esto SIEMPRE se ejecuta, venga de donde venga el dato
    return task;
}
```

La clave está en qué se anotó con `@Cacheable`: solo `CachedTaskLookup.findById(Long id)`, que no recibe ni conoce al usuario actual. Si en cambio se hubiera anotado un método que tomara `(id, currentUser)` y devolviera directamente la `Task` ya autorizada, un segundo usuario sin permiso podría beneficiarse de la decisión de acceso tomada para el primero — la caché estaría memorizando una autorización, no solo un dato. Separar "traer el dato" (cacheable) de "autorizar el acceso" (siempre se ejecuta) es la decisión de diseño que evita esa fuga.
