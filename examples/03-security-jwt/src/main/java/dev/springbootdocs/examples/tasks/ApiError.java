package dev.springbootdocs.examples.tasks;

import java.time.Instant;

public record ApiError(int status, String message, String timestamp) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Instant.now().toString());
    }
}
