---
title: Validación
sidebar_position: 5
---

# Validación

Cuando un cliente envía datos incorrectos (por ejemplo, una tarea sin título), la API debería rechazarlos con un **400 Bad Request** claro, antes de que lleguen a la lógica de negocio. Spring Boot integra **Bean Validation** para esto.

## Anotar las restricciones

Las restricciones se declaran directamente en el objeto que representa la petición:

```java
public record TaskRequest(@NotBlank String titulo, String descripcion, boolean completada) {
}
```

`@NotBlank` (de `jakarta.validation.constraints`) exige que `titulo` no sea `null`, ni una cadena vacía, ni solo espacios en blanco. Bean Validation ofrece muchas más: `@NotNull`, `@Size(min=, max=)`, `@Email`, `@Min`/`@Max`, etc.

## Activar la validación con `@Valid`

Anotar el campo no basta: hay que decirle al controlador que valide antes de ejecutar el método, con `@Valid`:

```java
@PostMapping("/tasks")
public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest request) {
    Task created = taskService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

Si `request` no cumple sus restricciones, Spring lanza una `MethodArgumentNotValidException` **antes** de que el cuerpo del método se ejecute — `taskService.create(request)` nunca llega a llamarse con datos inválidos.

## ¿Qué le llega al cliente?

Por defecto, esa excepción produciría una respuesta 400 genérica y poco útil. En [Manejo de errores](./manejo-errores) se explica cómo capturarla para devolver, en su lugar, algo como:

```json
{
  "status": 400,
  "message": "Datos de la petición inválidos",
  "timestamp": "2026-09-14T18:30:00Z",
  "errors": {
    "titulo": "must not be blank"
  }
}
```

De modo que quien consuma la API sepa exactamente qué campo falló y por qué.
