package dev.springbootdocs.examples.tasks.exception;

import java.time.Instant;
import java.util.Map;

public record ValidationApiError(int status, String message, String timestamp, Map<String, String> errors) {

    public static ValidationApiError of(int status, String message, Map<String, String> errors) {
        return new ValidationApiError(status, message, Instant.now().toString(), errors);
    }
}
