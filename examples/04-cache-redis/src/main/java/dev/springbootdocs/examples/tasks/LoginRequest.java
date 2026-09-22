package dev.springbootdocs.examples.tasks;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
