---
title: Mapear un esquema heredado
sidebar_position: 1
---

# Mapear un esquema heredado

En la Fase 2 los nombres iban de Java a la base de datos: la entidad `Task` se llamaba así, sus campos `titulo` y `completada`, y la migración de Flyway creaba una tabla a juego. Ahora es al revés: la tabla ya existe, se llama `TB_TAREA`, y la entidad tiene que encajar en ella sin cambiarle nada.

## `ddl-auto: validate`: la red de seguridad

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Con `validate`, Hibernate **no crea ni modifica nada**. Al arrancar compara cada entidad con el esquema real (tablas, columnas, tipos, secuencias) y, si algo no encaja, la aplicación no arranca y el error dice exactamente qué falla. Contra una base de datos heredada es justo lo que quieres: te enteras de un nombre mal escrito al arrancar, no cuando un cliente pide esa tarea.

La alternativa es `none`: Hibernate ni crea ni comprueba. Déjala para cuando `validate` no sea capaz de entender algún tipo muy particular del esquema; el precio es que los errores de mapeo aparecen en tiempo de ejecución, en la primera consulta que los toque. Lo que no debes usar nunca contra una base de datos ajena es `update` o `create`: Hibernate intentaría cambiar un esquema que no es tuyo.

## Nombres: `@Table` y `@Column`

```java
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

    // ...
}
```

Los campos de Java conservan nombres legibles (`titulo`, `descripcion`) y cada `@Column(name = ...)` dice a qué columna corresponden. El resto de la aplicación (servicios, DTOs, JSON) nunca ve `DS_TITULO`: el nombre heredado se queda en la entidad.

Dos detalles sobre cómo llega ese nombre a la base de datos:

- **Spring Boot pasa también los nombres explícitos por su estrategia de nombres**, que convierte a minúsculas y separa con guiones bajos las palabras en *camelCase*. `DS_TITULO` acaba como `ds_titulo`.
- **Postgres guarda en minúsculas los identificadores escritos sin comillas.** El DBA escribió `CREATE TABLE TB_TAREA`, pero en el catálogo la tabla se llama `tb_tarea`. Por eso encajan: los dos lados acaban en minúsculas.

:::caution[Identificadores entre comillas]
Si el esquema se creó con comillas, por ejemplo `CREATE TABLE "TareasArchivadas"`, Postgres respeta las mayúsculas y el nombre real es `TareasArchivadas`, no `tareasarchivadas`. Mapeado como `@Table(name = "TareasArchivadas")`, la estrategia de nombres lo convierte en `tareas_archivadas` y `validate` responde `Schema validation: missing table [tareas_archivadas]`. La solución es escribir las comillas también en el mapeo, escapadas: `@Table(name = "\"TareasArchivadas\"")`. Con comillas, Spring Boot respeta el nombre tal cual.
:::

## La secuencia que ya existe

```sql
CREATE SEQUENCE SQ_TAREA START WITH 1000 INCREMENT BY 1;
```

Con `GenerationType.SEQUENCE`, Hibernate pide cada id a esa secuencia. Pero por defecto no la consulta en cada `INSERT`: con `allocationSize = 50` (el valor por defecto) pide un número y da por hecho que tiene reservados los 50 siguientes, porque espera que la secuencia avance de 50 en 50. Esta avanza de uno en uno, y además **la usa otra aplicación**: si Hibernate se reservara 50 ids que la secuencia no ha reservado, acabaría repitiendo ids que la otra aplicación ya ha usado.

Hibernate lo detecta al arrancar. Sin `allocationSize = 1`, la aplicación no arranca:

```
org.hibernate.MappingException: The increment size of the [sq_tarea] sequence is set to [50] in the entity mapping but the mapped database sequence increment size is [1]
```

Con `allocationSize = 1`, cada `INSERT` pide su número a la secuencia, igual que hace la otra aplicación con el `DEFAULT nextval('SQ_TAREA')` de la columna. Las dos sacan números del mismo sitio y nunca chocan.

## Una bandera `'S'`/`'N'`: `AttributeConverter`

`FL_COMPLETADA` es un `CHAR(1)` que vale `'S'` o `'N'`. En Java queremos un `boolean`. Un `AttributeConverter` hace la traducción en los dos sentidos:

```java
@Converter
public class SiNoConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "S" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        return switch (dbValue) {
            case "S" -> true;
            case "N" -> false;
            default -> throw new IllegalArgumentException(
                    "Se esperaba 'S' o 'N' en la base de datos y llegó '" + dbValue + "'");
        };
    }
}
```

Un valor desconocido lanza una excepción en vez de tratarse como `false`: en una base de datos compartida, un valor raro significa que algo no es como creías, y es mejor enterarse. Hibernate trae convertidores para los casos más comunes (`YesNoConverter` para `'Y'`/`'N'`, `TrueFalseConverter` para `'T'`/`'F'`, `NumericBooleanConverter` para `1`/`0`), pero ninguno entiende `'S'`/`'N'`.

En la entidad:

```java
@Convert(converter = SiNoConverter.class)
@JdbcTypeCode(SqlTypes.CHAR)
@Column(name = "FL_COMPLETADA", nullable = false, length = 1)
private boolean completada;
```

`@JdbcTypeCode(SqlTypes.CHAR)` no es decorativo. El convertidor produce un `String`, y para un `String` Hibernate espera una columna `VARCHAR`. Sin esa línea, `validate` rechaza la columna:

```
Schema validation: wrong column type encountered in column [fl_completada] in table [tb_tarea]; found [bpchar (Types#CHAR)], but expecting [varchar(1) (Types#VARCHAR)]
```

(`bpchar` es el nombre interno de `CHAR` en Postgres.) Con `@JdbcTypeCode(SqlTypes.CHAR)` le dices a Hibernate qué tipo tiene de verdad la columna.

## Una clave primaria compuesta: `@EmbeddedId`

Los comentarios no tienen un id propio: se identifican por la tarea y un número de línea dentro de ella.

```sql
CREATE TABLE TB_COMENTARIO_TAREA (
    ID_TAREA  BIGINT       NOT NULL REFERENCES TB_TAREA (ID_TAREA),
    NU_LINEA  INTEGER      NOT NULL,
    DS_TEXTO  VARCHAR(500) NOT NULL,
    FH_ALTA   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ID_TAREA, NU_LINEA)
);
```

La clave compuesta se modela como un tipo propio, marcado con `@Embeddable`. Un `record` sirve:

```java
@Embeddable
public record TaskCommentId(
        @Column(name = "ID_TAREA") Long taskId,
        @Column(name = "NU_LINEA") Integer linea) implements Serializable {
}
```

Y la entidad lo usa como id con `@EmbeddedId`:

```java
@Entity
@Table(name = "TB_COMENTARIO_TAREA")
public class TaskComment {

    @EmbeddedId
    private TaskCommentId id;

    @Column(name = "DS_TEXTO", nullable = false, length = 500)
    private String texto;

    // ...
}
```

El repositorio usa el tipo de la clave como segundo parámetro, y las consultas derivadas pueden entrar en ella (`IdTaskId` es "el `taskId` del `id`"):

```java
public interface TaskCommentRepository extends JpaRepository<TaskComment, TaskCommentId> {

    List<TaskComment> findByIdTaskIdOrderByIdLinea(Long taskId);

    @Query("select coalesce(max(c.id.linea), 0) from TaskComment c where c.id.taskId = :taskId")
    int findMaxLinea(Long taskId);

    // ...
}
```

El número de línea lo calcula el servicio como la última línea de la tarea más uno, que es lo que hacía la aplicación antigua. Con un id que asigna la propia aplicación hay una consecuencia que conviene conocer: `save()` no tiene forma de saber si la entidad es nueva (el id ya viene relleno), así que hace un `merge`, que primero lanza un `SELECT` para buscarla y después el `INSERT`. En este ejemplo no importa. Si importara, la entidad puede implementar `Persistable<TaskCommentId>` y decir ella misma si es nueva.

(La otra forma de mapear una clave compuesta es `@IdClass`, que deja los campos de la clave directamente en la entidad. `@EmbeddedId` agrupa la clave en un solo objeto y es la más habitual.)
