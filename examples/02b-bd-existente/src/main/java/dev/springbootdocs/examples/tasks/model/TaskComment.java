package dev.springbootdocs.examples.tasks.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "TB_COMENTARIO_TAREA")
public class TaskComment {

    @EmbeddedId
    private TaskCommentId id;

    @Column(name = "DS_TEXTO", nullable = false, length = 500)
    private String texto;

    @Generated
    @Column(name = "FH_ALTA", updatable = false)
    private LocalDateTime fechaAlta;

    protected TaskComment() {
    }

    public TaskComment(TaskCommentId id, String texto) {
        this.id = id;
        this.texto = texto;
    }

    public TaskCommentId getId() {
        return id;
    }

    public String getTexto() {
        return texto;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }
}
