package dev.springbootdocs.examples.tasks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "VW_RESUMEN_TAREA")
public class TaskSummary {

    @Id
    @Column(name = "ID_TAREA")
    private Long id;

    @Column(name = "DS_TITULO")
    private String titulo;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FL_COMPLETADA", length = 1)
    private boolean completada;

    @Column(name = "NU_COMENTARIOS")
    private long numComentarios;

    @Column(name = "FH_ULTIMO_COMENTARIO")
    private LocalDateTime fechaUltimoComentario;

    protected TaskSummary() {
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public boolean isCompletada() {
        return completada;
    }

    public long getNumComentarios() {
        return numComentarios;
    }

    public LocalDateTime getFechaUltimoComentario() {
        return fechaUltimoComentario;
    }
}
