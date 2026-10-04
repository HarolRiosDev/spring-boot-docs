package dev.springbootdocs.examples.tasks.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    // Un sort por un campo que no está en la lista de campos ordenables
    @ExceptionHandler(InvalidSortException.class)
    public ResponseEntity<ApiError> handleInvalidSort(InvalidSortException ex) {
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Un parámetro de la URL que no se puede convertir a su tipo: ?completada=quizas o /tasks/abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(),
                "El parámetro '" + ex.getName() + "' tiene un valor no válido: '" + ex.getValue() + "'");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
