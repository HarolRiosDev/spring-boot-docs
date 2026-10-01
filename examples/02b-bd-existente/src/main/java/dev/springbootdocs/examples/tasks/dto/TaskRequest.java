package dev.springbootdocs.examples.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Los @Size coinciden con las longitudes de las columnas de TB_TAREA.
// completada es opcional: Boolean y no boolean, porque Jackson 3 rechaza un JSON
// al que le falta un campo primitivo. Si no viene, la tarea queda pendiente.
public record TaskRequest(
        @NotBlank @Size(max = 255) String titulo,
        @Size(max = 1000) String descripcion,
        Boolean completada) {

    public TaskRequest {
        if (completada == null) {
            completada = false;
        }
    }
}
