package dev.springbootdocs.examples.tasks.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "TB_COMENTARIO_TAREA")
public class TaskComment implements Persistable<TaskCommentId> {

    @EmbeddedId
    private TaskCommentId id;

    @Column(name = "DS_TEXTO", nullable = false, length = 500)
    private String texto;

    @Generated
    @Column(name = "FH_ALTA", updatable = false)
    private LocalDateTime fechaAlta;

    // El id lo asigna la aplicación: sin esto, save() haría merge y pisaría una línea que ya existe
    @Transient
    private boolean isNew = true;

    protected TaskComment() {
    }

    public TaskComment(TaskCommentId id, String texto) {
        this.id = id;
        this.texto = texto;
    }

    @Override
    public TaskCommentId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    public String getTexto() {
        return texto;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }
}
