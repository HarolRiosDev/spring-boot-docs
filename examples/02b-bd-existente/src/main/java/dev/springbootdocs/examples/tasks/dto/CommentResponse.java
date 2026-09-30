package dev.springbootdocs.examples.tasks.dto;

import dev.springbootdocs.examples.tasks.model.TaskComment;
import java.time.LocalDateTime;

public record CommentResponse(Long taskId, Integer linea, String texto, LocalDateTime fechaAlta) {

    public static CommentResponse from(TaskComment comment) {
        return new CommentResponse(comment.getId().taskId(), comment.getId().linea(),
                comment.getTexto(), comment.getFechaAlta());
    }
}
