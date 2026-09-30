package dev.springbootdocs.examples.tasks.exception;

import java.time.Instant;

public record ApiError(int status, String message, String timestamp) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Instant.now().toString());
    }
}
