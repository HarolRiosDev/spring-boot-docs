package dev.springbootdocs.examples.tasks.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

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

    // Validación de parámetros sueltos (un @RequestParam con @Min, por ejemplo)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ValidationApiError> handleParameterValidation(HandlerMethodValidationException ex) {
        Map<String, String> paramErrors = new HashMap<>();
        ex.getParameterValidationResults().forEach(result -> paramErrors.put(
                result.getMethodParameter().getParameterName(),
                result.getResolvableErrors().getFirst().getDefaultMessage()));
        ValidationApiError error = ValidationApiError.of(
                HttpStatus.BAD_REQUEST.value(), "Parámetros de la petición inválidos", paramErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(TaskVersionConflictException.class)
    public ResponseEntity<ApiError> handleVersionConflict(TaskVersionConflictException ex) {
        ApiError error = ApiError.of(HttpStatus.CONFLICT.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // El cambio ajeno llegó entre nuestra lectura y el UPDATE: lo detecta @Version al hacer flush
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ApiError error = ApiError.of(HttpStatus.CONFLICT.value(),
                "La tarea cambió mientras se guardaba; vuelve a leerla antes de modificarla");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
