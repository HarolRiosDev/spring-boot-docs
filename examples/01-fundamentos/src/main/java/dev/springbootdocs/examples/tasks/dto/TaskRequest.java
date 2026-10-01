package dev.springbootdocs.examples.tasks.dto;

import jakarta.validation.constraints.NotBlank;

// completada es opcional: Boolean y no boolean, porque Jackson 3 rechaza un JSON
// al que le falta un campo primitivo. Si no viene, la tarea queda pendiente.
public record TaskRequest(@NotBlank String titulo, String descripcion, Boolean completada) {

    public TaskRequest {
        if (completada == null) {
            completada = false;
        }
    }
}
