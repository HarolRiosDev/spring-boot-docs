---
title: Roles y autorización
sidebar_position: 3
---

# Roles y autorización

Esta fase combina dos mecanismos de autorización distintos, cada uno donde encaja mejor.

## Ownership: ¿de quién es esta tarea?

Cada `Task` tiene un dueño (`user_id`). Decidir si la petición actual puede acceder a una tarea concreta requiere primero cargarla y compararla contra quién hace la petición — eso no es un simple "¿tiene este rol?", así que vive como código explícito en `TaskServiceImpl`, no en una anotación:

```java
@Override
@Transactional(readOnly = true)
public TaskResponse findById(Long id, User currentUser) {
    return TaskResponse.from(getTaskForCurrentUser(id, currentUser));
}

private Task getTaskForCurrentUser(Long id, User currentUser) {
    Task task = taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
    requireAccess(task, currentUser);
    return task;
}

private void requireAccess(Task task, User currentUser) {
    boolean isOwner = task.getUser().getId().equals(currentUser.getId());
    boolean isAdmin = currentUser.getRole() == Role.ADMIN;
    if (!isOwner && !isAdmin) {
        throw new TaskAccessDeniedException(task.getId());
    }
}
```

Una tarea ajena para un `USER` devuelve **403 Forbidden**, nunca 404 — 404 se reserva para "esta tarea no existe para nadie". Mezclarlos filtraría información: un 404 en vez de un 403 le diría a un atacante que probó IDs al azar cuáles existen y cuáles no.

`update` y `delete` empiezan llamando a `getTaskForCurrentUser`, así que heredan la misma comprobación sin repetirla. Ese método privado devuelve la entidad `Task` (la necesitan para modificarla o borrarla); solo `findById` la convierte en `TaskResponse`, el DTO que sale hacia el cliente.

## `@PreAuthorize`: autorización declarativa por rol

Para un caso más simple — "solo un `ADMIN` puede llamar a este endpoint, sin más matices" — una anotación es más clara que código a mano:

```java
@GetMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public List<UserSummary> listUsers() {
    return userService.findAll();
}
```

`hasRole('ADMIN')` compara contra las autoridades del usuario autenticado (`ROLE_ADMIN`, que añade `UserPrincipal.getAuthorities()` — el prefijo `ROLE_` lo añade automáticamente `hasRole`, no hace falta escribirlo). Si un `USER` normal llama a este endpoint, Spring Security lanza `AccessDeniedException` **antes** de que se ejecute el cuerpo del método — nunca llega a `userService.findAll()`.

Aunque el endpoint solo lista usuarios, el controlador no llama a `UserRepository` directamente: pasa por `UserService`, que hace la consulta y convierte cada `User` en un `UserSummary` (el DTO que evita exponer el hash de la contraseña). Incluso una lectura trivial respeta las [capas](/docs/01-fundamentos/capas) — si un controlador sí puede saltarse el servicio y el de al lado no, la regla deja de ser una regla.

Nota el porqué de elegir uno u otro: `/admin/users` es "todo o nada" según el rol, así que `@PreAuthorize` es la herramienta correcta. `/tasks/{id}` depende de datos (¿es tuya o no?) que no existen todavía cuando se evalúa la anotación — por eso vive en el servicio.

## Por qué el rol se recarga desde la base de datos, no del JWT

El JWT lleva un claim `"role"` (ver [Autenticación con JWT](./autenticacion-jwt)), pero `JwtAuthenticationFilter` no lo usa para decidir permisos — vuelve a cargar el `User` completo con `UserDetailsServiceImpl` en cada petición. Es más trabajo (una consulta a la base de datos por petición) a cambio de una garantía real: si cambias el rol de un usuario en la base de datos, el cambio se aplica en su siguiente petición, sin esperar a que expire un token que ya tenía el rol antiguo grabado.

## Respuestas de error consistentes

Por defecto, Spring Security no devuelve nada útil para un cliente de API: con `formLogin()` redirige a una página de login (pensado para un navegador), y sin él —como aquí— responde con el cuerpo vacío. Peor aún, sin `formLogin()` ni `httpBasic()` el punto de entrada por defecto (`Http403ForbiddenEntryPoint`) responde **403** también cuando falta el token, en vez del 401 que corresponde. Dos componentes propios lo sustituyen por el mismo formato `ApiError` que ya usa `GlobalExceptionHandler`:

```java
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    // 401 — sin token, token inválido o expirado
}

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    // 403 — autenticado, pero @PreAuthorize rechaza la petición
}
```

Se registran en `SecurityConfig` con `.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(...).accessDeniedHandler(...))`. El 403 de una tarea ajena, en cambio, no pasa por aquí — lo lanza `TaskAccessDeniedException` desde el servicio, y lo captura `GlobalExceptionHandler` como cualquier otra excepción de negocio.
