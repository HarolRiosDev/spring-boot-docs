package dev.springbootdocs.examples.tasks.dto;

import dev.springbootdocs.examples.tasks.model.TaskSummary;
import java.time.LocalDateTime;

public record TaskSummaryResponse(
        Long id,
        String titulo,
        boolean completada,
        long numComentarios,
        LocalDateTime fechaUltimoComentario) {

    public static TaskSummaryResponse from(TaskSummary summary) {
        return new TaskSummaryResponse(summary.getId(), summary.getTitulo(), summary.isCompletada(),
                summary.getNumComentarios(), summary.getFechaUltimoComentario());
    }
}
