package dev.springbootdocs.examples.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Los @Size coinciden con las longitudes de las columnas de TB_TAREA
public record TaskRequest(
        @NotBlank @Size(max = 255) String titulo,
        @Size(max = 1000) String descripcion,
        boolean completada) {
}
