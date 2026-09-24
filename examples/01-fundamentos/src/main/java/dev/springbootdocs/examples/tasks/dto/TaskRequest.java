package dev.springbootdocs.examples.tasks.dto;

import jakarta.validation.constraints.NotBlank;

public record TaskRequest(@NotBlank String titulo, String descripcion, boolean completada) {
}
