---
title: Vistas y convivencia con otras aplicaciones
sidebar_position: 3
---

# Vistas y convivencia con otras aplicaciones

Una base de datos heredada no es un almacén pasivo: tiene vistas que otros usan para informes, columnas que rellena ella sola y otras aplicaciones escribiendo a la vez que la tuya. Esta página va por partes.

## Una vista como entidad de solo lectura

```sql
CREATE VIEW VW_RESUMEN_TAREA AS
SELECT t.ID_TAREA,
       t.DS_TITULO,
       t.FL_COMPLETADA,
       COUNT(c.NU_LINEA) AS NU_COMENTARIOS,
       MAX(c.FH_ALTA)    AS FH_ULTIMO_COMENTARIO
  FROM TB_TAREA t
  LEFT JOIN TB_COMENTARIO_TAREA c ON c.ID_TAREA = t.ID_TAREA
 GROUP BY t.ID_TAREA, t.DS_TITULO, t.FL_COMPLETADA;
```

Para JPA, una vista es una tabla más: se mapea con `@Entity` y `@Table`. Lo que cambia es que no se puede escribir en ella, y eso se dice en dos sitios:

```java
@Entity
@Immutable
@Table(name = "VW_RESUMEN_TAREA")
public class TaskSummary {

    @Id
    @Column(name = "ID_TAREA")
    private Long id;

    @Column(name = "NU_COMENTARIOS")
    private long numComentarios;

    @Column(name = "FH_ULTIMO_COMENTARIO")
    private LocalDateTime fechaUltimoComentario;

    // titulo, completada y getters; sin setters
}
```

```java
// Repository (no JpaRepository): la vista es de solo lectura, así que no exponemos save ni delete
public interface TaskSummaryRepository extends Repository<TaskSummary, Long> {

    List<TaskSummary> findAllByOrderByIdAsc();
}
```

- `@Immutable` (de Hibernate) le dice que nunca genere un `UPDATE` para esta entidad, aunque alguien cambie un campo.
- Extender `Repository` en vez de `JpaRepository` hace que el repositorio solo tenga los métodos que declaras: no existe un `save()` que alguien pueda llamar por error.

La vista necesita una columna que identifique cada fila de forma única para usarla como `@Id`; aquí, `ID_TAREA`. Y `validate` también comprueba las vistas: una columna mal escrita en `TaskSummary` impide arrancar igual que en una tabla (`Schema validation: missing column [...] in table [vw_resumen_tarea]`).

## Columnas que rellena la base de datos

Dos columnas de `TB_TAREA` no las escribe nadie desde fuera:

```sql
FH_ALTA         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- la pone el DEFAULT
FH_MODIFICACION TIMESTAMP                                          -- la pone un trigger
```

```sql
CREATE FUNCTION FN_TR_TAREA_MODIFICADA() RETURNS TRIGGER AS $$
BEGIN
    NEW.FH_MODIFICACION := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER TR_TAREA_MODIFICADA
    BEFORE UPDATE ON TB_TAREA
    FOR EACH ROW EXECUTE FUNCTION FN_TR_TAREA_MODIFICADA();
```

`@Generated` le dice a Hibernate que el valor lo genera la base de datos y que tiene que leerlo después de escribir:

```java
// La BD la rellena al insertar (DEFAULT) y la aplicación no la modifica nunca
@Generated
@Column(name = "FH_ALTA", updatable = false)
private LocalDateTime fechaAlta;

// La rellena el trigger en cada UPDATE; Hibernate la relee después de actualizar
@Generated(event = EventType.UPDATE)
@Column(name = "FH_MODIFICACION", insertable = false)
private LocalDateTime fechaModificacion;
```

Con Postgres, Hibernate relee el valor en la misma sentencia, sin una consulta extra:

```sql
insert into tb_tarea (fl_completada,ds_descripcion,ds_titulo,nu_version,id_tarea) values (?,?,?,?,?) returning fh_alta
update tb_tarea set fl_completada=?,ds_descripcion=?,ds_titulo=?,nu_version=? where id_tarea=? and nu_version=? returning fh_modificacion
```

:::caution[`@Generated` solo cubre el evento que declara]
`@Generated` (sin parámetros) significa "la genera la base de datos al insertar": Hibernate no la envía en el `INSERT`… pero sí en cada `UPDATE`, con el valor que leyó. Sin `updatable = false`, cada actualización reescribiría `fh_alta`. Con `FH_MODIFICACION` pasa lo contrario: `@Generated(event = EventType.UPDATE)` la excluye del `UPDATE`, pero el `INSERT` la enviaría (a `null`) sin `insertable = false`. En una columna que es de la base de datos, marca los dos eventos.
:::

Falta una pieza. Con ids de secuencia, Hibernate no ejecuta el `INSERT` en el `save()`: lo deja para el `flush`, normalmente al confirmar la transacción. Si el servicio construye la respuesta antes, `fechaAlta` todavía es `null`. Por eso el servicio fuerza el `flush`:

```java
@Override
@Transactional
public TaskResponse create(TaskRequest request) {
    Task task = new Task(request.titulo(), request.descripcion(), request.completada());
    // saveAndFlush: el INSERT se ejecuta ya, y con él llegan los valores que genera la BD (FH_ALTA)
    return TaskResponse.from(taskRepository.saveAndFlush(task));
}
```

Al actualizar pasa lo mismo con `FH_MODIFICACION`, y por eso `update` llama a `taskRepository.flush()` antes de construir la respuesta.

## Otra aplicación escribe en la misma tabla

Este es el problema más serio de una base de datos compartida. Imagina esta secuencia:

1. Tu cliente lee la tarea 1004 (título "Renovar los certificados").
2. La otra aplicación cambia el título de la tarea 1004.
3. Tu cliente envía su `PUT` con los datos que leyó en el paso 1, más su cambio.

Sin más, el paso 3 **pisa en silencio** el cambio del paso 2: es la *actualización perdida*. La herramienta de JPA para evitarla es el bloqueo optimista con `@Version`: una columna de versión que Hibernate incrementa en cada `UPDATE` y comprueba en el `WHERE` (`where id_tarea=? and nu_version=?`). Si otra escritura se adelantó, la condición no encuentra la fila y la actualización falla en vez de pisar nada.

Pero `@Version` solo funciona si **todos** los que escriben respetan la columna. La otra aplicación no sabe que `NU_VERSION` existe: su `UPDATE` no la incrementa, así que para Hibernate la fila sigue "sin cambios". La solución del ejemplo es que la versión la suba la propia base de datos, en el trigger. Es la primera migración propia del equipo:

```sql
-- V2__control_de_concurrencia.sql
ALTER TABLE TB_TAREA ADD COLUMN NU_VERSION INTEGER NOT NULL DEFAULT 0;

-- Las otras aplicaciones que escriben en TB_TAREA no conocen NU_VERSION:
-- es el trigger quien la incrementa en todo UPDATE, venga de quien venga.
CREATE OR REPLACE FUNCTION FN_TR_TAREA_MODIFICADA() RETURNS TRIGGER AS $$
BEGIN
    NEW.FH_MODIFICACION := now();
    NEW.NU_VERSION := OLD.NU_VERSION + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

```java
@Version
@Column(name = "NU_VERSION")
private Integer version;
```

¿No se incrementa dos veces cuando escribe Hibernate? No: Hibernate escribe `versión + 1` y el trigger calcula `OLD.NU_VERSION + 1`, que es el mismo número. Una sola subida.

Queda un detalle: el paso 1 y el paso 3 son **peticiones HTTP distintas**, en transacciones distintas. `@Version` compara la versión de la entidad que se cargó en la transacción del `PUT`, que ya es la nueva. Por eso el cliente envía en el `PUT` la versión que leyó, y el servicio la compara:

```java
@Override
@Transactional
public TaskResponse update(Long id, TaskUpdateRequest request) {
    Task task = getTask(id);
    if (!task.getVersion().equals(request.version())) {
        throw new TaskVersionConflictException(id, request.version(), task.getVersion());
    }
    task.setTitulo(request.titulo());
    task.setDescripcion(request.descripcion());
    task.setCompletada(request.completada());
    // flush: el UPDATE se ejecuta ya, y Hibernate relee FH_MODIFICACION (la pone el trigger)
    taskRepository.flush();
    return TaskResponse.from(task);
}
```

`GlobalExceptionHandler` convierte `TaskVersionConflictException` en un **409 Conflict**. Si el cambio ajeno llega justo entre la lectura y el `flush` de esta misma transacción, el que salta es `@Version`, con una `ObjectOptimisticLockingFailureException`, que el mismo handler convierte también en 409. En la práctica:

```bash
# La otra aplicación cambia la tarea 1004 (versión 0) sin saber que NU_VERSION existe
docker compose exec postgres psql -U legacy legacy \
  -c "UPDATE TB_TAREA SET DS_TITULO = 'Cambiada por la otra aplicación' WHERE ID_TAREA = 1004"

# Nuestro PUT con la versión que habíamos leído
curl -s -X PUT localhost:8080/tasks/1004 -H 'Content-Type: application/json' \
  -d '{"titulo":"Mi versión","completada":false,"version":0}'
```

```json
{"status":409,"message":"La tarea 1004 cambió desde que la leíste (leíste la versión 0, la actual es la 1); vuelve a leerla antes de modificarla","timestamp":"..."}
```

Sin el trigger de `V2`, ese mismo `PUT` responde 200 y el título de la otra aplicación desaparece. El test `TaskConcurrencyIT` comprueba este flujo.

## Claves foráneas sin cascada

```sql
ID_TAREA BIGINT NOT NULL REFERENCES TB_TAREA (ID_TAREA)
```

La clave foránea de los comentarios no tiene `ON DELETE CASCADE`, algo muy habitual en esquemas heredados. Borrar una tarea con comentarios falla en la base de datos:

```
ERROR: update or delete on table "tb_tarea" violates foreign key constraint "tb_comentario_tarea_id_tarea_fkey" on table "tb_comentario_tarea"
```

No toca cambiar el esquema; toca que el servicio borre en el orden correcto, dentro de la misma transacción:

```java
@Override
@Transactional
public void delete(Long id) {
    Task task = getTask(id);
    // La clave foránea no tiene ON DELETE CASCADE: primero los comentarios
    taskCommentRepository.deleteByTaskId(id);
    taskRepository.delete(task);
}
```

## Una limitación heredada: la línea `max + 1`

El número de línea de un comentario nuevo es la última línea de esa tarea más uno, igual que en la aplicación antigua. Si dos personas comentan la misma tarea a la vez, las dos pueden calcular el mismo número. La clave primaria impide que se guarde un dato corrupto, pero una de las dos peticiones falla. Arreglarlo de verdad (con una secuencia o una columna de identidad) es un cambio de esquema que habría que acordar con el resto de equipos; mientras tanto, conviene saber que existe.
