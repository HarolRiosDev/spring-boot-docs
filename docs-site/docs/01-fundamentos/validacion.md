---
title: Validación
sidebar_position: 5
---

# Validación

Cuando un cliente envía datos incorrectos (por ejemplo, una tarea sin título), la API debería rechazarlos con un **400 Bad Request** claro, antes de que lleguen a la lógica de negocio. Spring Boot integra **Bean Validation** para esto.

## Anotar las restricciones

Las restricciones se declaran directamente en el objeto que representa la petición:

```java
public record TaskRequest(@NotBlank String titulo, String descripcion, Boolean completada) {

    public TaskRequest {
        if (completada == null) {
            completada = false;
        }
    }
}
```

`@NotBlank` (de `jakarta.validation.constraints`) exige que `titulo` no sea `null`, ni una cadena vacía, ni solo espacios en blanco. Bean Validation ofrece muchas más: `@NotNull`, `@Size(min=, max=)`, `@Email`, `@Min`/`@Max`, etc.

## Un campo opcional: `Boolean`, no `boolean`

`completada` es opcional: quien crea una tarea normalmente solo manda el título, `{"titulo": "Comprar pan"}`. Por eso es `Boolean` (la clase envoltorio) y no el primitivo `boolean`.

Un `boolean` no puede valer `null`, así que no tiene forma de representar "el cliente no lo envió". Jackson, la librería que convierte el JSON en el record, no se lo inventa: en Jackson 3, la versión que usa Spring Boot 4, la opción `FAIL_ON_NULL_FOR_PRIMITIVES` viene activada, y un campo primitivo que falta hace fallar la lectura del JSON entero. Una petición correcta acabaría en un 400. Muchos tutoriales escritos para Spring Boot 3 usan `boolean` sin problema porque Jackson 2 rellenaba el hueco con `false`.

Con `Boolean`, el campo ausente llega como `null`, y el **constructor compacto** del record (el bloque `public TaskRequest { ... }`, que se ejecuta antes de asignar los campos) lo convierte en `false`. La tarea se crea pendiente, y el resto del código lee `request.completada()` como cualquier `boolean`, sin preocuparse de un `null` que ya no puede aparecer.

En un `PUT` pasa lo mismo: si no se manda `completada`, la tarea queda pendiente, porque `PUT` sustituye la tarea entera por lo que llega.

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

El texto `"must not be blank"` es el mensaje por defecto de Bean Validation, y su idioma lo decide la cabecera `Accept-Language` de cada petición: con `en` llega `"must not be blank"`, y con `es`, `"no debe estar vacío"`. Si la petición no la manda (`curl`, por ejemplo, no la manda), se usa el idioma del sistema donde corre la aplicación. Si quieres un mensaje fijo, indícalo en la propia anotación: `@NotBlank(message = "El título es obligatorio")`.
