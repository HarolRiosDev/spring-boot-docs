package dev.springbootdocs.examples.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// version es la que el cliente leyó: si la tarea cambió desde entonces, la actualización se rechaza
public record TaskUpdateRequest(
        @NotBlank @Size(max = 255) String titulo,
        @Size(max = 1000) String descripcion,
        boolean completada,
        @NotNull Integer version) {
}
