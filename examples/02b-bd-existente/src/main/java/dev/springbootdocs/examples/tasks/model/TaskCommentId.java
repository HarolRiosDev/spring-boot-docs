package dev.springbootdocs.examples.tasks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record TaskCommentId(
        @Column(name = "ID_TAREA") Long taskId,
        @Column(name = "NU_LINEA") Integer linea) implements Serializable {
}
