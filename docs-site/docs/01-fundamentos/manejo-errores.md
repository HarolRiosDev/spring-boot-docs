---
title: Manejo de errores
sidebar_position: 6
---

# Manejo de errores

Cuando algo va mal (una tarea que no existe, una petición inválida), la API debe devolver un código de estado correcto y un cuerpo JSON explicativo — no una traza de excepción cruda ni un 500 genérico.

## Excepciones propias

Cuando el service detecta un problema de negocio, lanza una excepción propia:

```java
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("No existe ninguna tarea con id " + id);
    }
}
```

```java
@Override
public Task findById(Long id) {
    return taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
}
```

El service no sabe (ni le importa) qué código HTTP corresponde a esta excepción — esa decisión vive en un único sitio, según se explica abajo.

## `@RestControllerAdvice`: un manejador centralizado

En vez de poner un `try/catch` en cada método del controller, Spring permite declarar manejadores globales con `@RestControllerAdvice`:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiError> handleTaskNotFound(TaskNotFoundException ex) {
        ApiError error = ApiError.of(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ValidationApiError error = ValidationApiError.of(
                HttpStatus.BAD_REQUEST.value(), "Datos de la petición inválidos", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // JSON mal formado o un campo con un tipo que no encaja (p. ej. "completada": "quizás")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(),
                "El cuerpo de la petición no es un JSON válido o tiene campos con un tipo incorrecto");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

Cada `@ExceptionHandler` intercepta un tipo de excepción concreto, lanzada desde *cualquier* controlador de la aplicación, y decide qué código de estado y qué cuerpo devolver.

## Un cuerpo que no se puede leer

Antes de validar nada, Spring tiene que convertir el JSON de la petición en un `TaskRequest`. Si no puede, porque el JSON está roto (`{"titulo": `) o porque un campo trae un tipo que no encaja (`"completada": "quizás"`), lanza una `HttpMessageNotReadableException` y `@Valid` ni siquiera llega a ejecutarse. El tercer manejador la convierte en un 400 con el mismo formato que los demás.

Su mensaje es genérico a propósito. El de la excepción (`ex.getMessage()`) describe el fallo con detalles internos de Jackson y nombres de clases Java, que no le sirven al cliente y le cuentan más de la cuenta sobre cómo está hecha la aplicación.

## Un formato de error consistente

Los tres manejadores devuelven una forma parecida (`status`, `message`, `timestamp`), para que quien consuma la API pueda parsear los errores de manera uniforme sin importar cuál ocurrió. Esta consistencia solo aplica a los casos manejados explícitamente: un error no contemplado aquí, como una ruta inexistente o un `GET /tasks/abc` con un id no numérico, cae en el manejo de errores por defecto de Spring, con una forma distinta.

```java
public record ApiError(int status, String message, String timestamp) { /* ... */ }

public record ValidationApiError(int status, String message, String timestamp, Map<String, String> errors) { /* ... */ }
```

`ValidationApiError` añade el mapa `errors` (campo → motivo del fallo), útil específicamente para errores de validación con varios campos incorrectos a la vez.

Con esto, el flujo completo de una petición inválida es: `@Valid` detecta el problema → lanza `MethodArgumentNotValidException` → `GlobalExceptionHandler` la captura → el cliente recibe un 400 con el detalle exacto de qué falló.
