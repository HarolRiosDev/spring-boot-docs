package dev.springbootdocs.examples.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank @Size(max = 255) String titulo, @Size(max = 1000) String descripcion, boolean completada) {
}
