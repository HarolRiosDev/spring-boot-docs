---
title: SQL fuera de JPA
sidebar_position: 4
---

# SQL fuera de JPA

JPA brilla leyendo y escribiendo entidades. No todo lo que necesitas de una base de datos heredada es eso: un informe que agrega miles de filas, una consulta que usa SQL propio del motor o una función que ya existe en la base de datos. Forzarlos a pasar por entidades suele salir peor que escribir el SQL. Spring trae `JdbcClient` para esos casos: una API fluida sobre JDBC que Spring Boot configura sola, con el mismo `DataSource` que usa JPA.

## Un informe con `JdbcClient`

Cuántas tareas se crearon cada día y cuántas de ellas están completadas:

```java
// SQL que no encaja en entidades: un informe con SQL propio de Postgres y una función que ya existe en la BD
@Repository
public class TaskJdbcRepository {

    private final JdbcClient jdbcClient;

    public TaskJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<DailyTaskCount> countPerDay() {
        return jdbcClient.sql("""
                        SELECT CAST(FH_ALTA AS DATE)                           AS dia,
                               COUNT(*)                                        AS creadas,
                               COUNT(*) FILTER (WHERE FL_COMPLETADA = 'S')     AS completadas
                          FROM TB_TAREA
                         GROUP BY dia
                         ORDER BY dia DESC
                        """)
                .query(DailyTaskCount.class)
                .list();
    }

    // ...
}
```

```java
// JdbcClient rellena este record directamente con las columnas del SELECT (dia, creadas, completadas)
public record DailyTaskCount(LocalDate dia, long creadas, long completadas) {
}
```

- `COUNT(*) FILTER (WHERE ...)` es SQL de Postgres: cuenta solo las filas que cumplen la condición. No hay que traducirlo a JPQL ni a nada; es el SQL que escribirías en `psql`.
- `.query(DailyTaskCount.class)` crea un `DailyTaskCount` por fila, emparejando cada columna del `SELECT` con el componente del `record` que se llama igual. Por eso los alias (`AS dia`, `AS creadas`…) coinciden con los nombres del `record`.
- Es una clase `@Repository` normal: el servicio la usa igual que un repositorio de Spring Data, y el controlador no sabe si detrás hay JPA o SQL.

Con los datos semilla y una tarea pendiente creada hoy, `GET /reports/tasks-per-day` devuelve un día por fila, del más reciente al más antiguo (las fechas dependen del día en que arrancó el contenedor):

```json
[
  {"dia":"2026-09-30","creadas":2,"completadas":0},
  {"dia":"2026-09-28","creadas":1,"completadas":0},
  {"dia":"2026-09-27","creadas":1,"completadas":1},
  {"dia":"2026-09-20","creadas":1,"completadas":0},
  {"dia":"2026-06-02","creadas":1,"completadas":1},
  {"dia":"2025-08-26","creadas":1,"completadas":1}
]
```

## Llamar a una función que ya existe

La base de datos heredada trae su propia lógica: `FN_PURGAR_COMPLETADAS` borra las tareas completadas hace más de N días, con sus comentarios, y devuelve cuántas borró. Otras aplicaciones la usan. Reescribirla en Java sería duplicarla, y las dos copias acabarían siendo distintas. Se llama:

```java
public int purgeCompleted(int days) {
    return jdbcClient.sql("SELECT FN_PURGAR_COMPLETADAS(:dias)")
            .param("dias", days)
            .query(Integer.class)
            .single();
}
```

En Postgres una función que devuelve un valor se llama con un `SELECT` normal, así que no hace falta nada especial. Para procedimientos con parámetros de salida existen `SimpleJdbcCall` y, en Spring Data JPA, `@Procedure`; con una función como esta, un `SELECT` es lo más directo.

`POST /tasks/purge?days=30` devuelve `{"purged": 2}`. El parámetro se valida en el controlador con `@RequestParam @Min(1) int days`: `days=0` responde 400 antes de llegar a la base de datos.

## JPA y `JdbcClient` en la misma transacción

`JdbcClient` y JPA usan la misma conexión cuando están dentro de la misma transacción de Spring (`JpaTransactionManager` comparte la conexión JDBC de Hibernate). En la práctica:

- Un `SELECT` con `JdbcClient` ve lo que JPA ya envió a la base de datos en esa transacción, aunque no se haya confirmado.
- Si la transacción se deshace, se deshace todo: lo de JPA y lo de `JdbcClient`.

Hay un matiz. JPA guarda en memoria las entidades que ha cargado (el contexto de persistencia), y el SQL directo pasa por debajo sin avisarle. Si en una transacción cargas una tarea con JPA y después la función la borra o la cambia, la entidad en memoria ya no refleja la base de datos. La regla práctica: haz `flush()` antes del SQL si JPA tiene cambios pendientes que ese SQL deba ver, y no reutilices después entidades que el SQL puede haber tocado (vuelve a leerlas). En el ejemplo la purga se ejecuta en una transacción propia que no ha cargado ninguna entidad, así que el problema no aparece.
