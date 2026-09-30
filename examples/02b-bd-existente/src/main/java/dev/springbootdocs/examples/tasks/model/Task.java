package dev.springbootdocs.examples.tasks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "TB_TAREA")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tarea")
    @SequenceGenerator(name = "tarea", sequenceName = "SQ_TAREA", allocationSize = 1)
    @Column(name = "ID_TAREA")
    private Long id;

    @Column(name = "DS_TITULO", nullable = false)
    private String titulo;

    @Column(name = "DS_DESCRIPCION", length = 1000)
    private String descripcion;

    @Convert(converter = SiNoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "FL_COMPLETADA", nullable = false, length = 1)
    private boolean completada;

    // La BD la rellena al insertar (DEFAULT) y la aplicación no la modifica nunca
    @Generated
    @Column(name = "FH_ALTA", updatable = false)
    private LocalDateTime fechaAlta;

    // La rellena el trigger en cada UPDATE; Hibernate la relee después de actualizar
    @Generated(event = EventType.UPDATE)
    @Column(name = "FH_MODIFICACION", insertable = false)
    private LocalDateTime fechaModificacion;

    @Version
    @Column(name = "NU_VERSION")
    private Integer version;

    protected Task() {
    }

    public Task(String titulo, String descripcion, boolean completada) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.completada = completada;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isCompletada() {
        return completada;
    }

    public void setCompletada(boolean completada) {
        this.completada = completada;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }

    public Integer getVersion() {
        return version;
    }
}
