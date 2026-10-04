---
title: Paginar y ordenar
sidebar_position: 1
---

# Paginar y ordenar

## Las tareas de partida

Para paginar hace falta algo que paginar. El ejemplo añade dos migraciones a la `V1` de la Fase 2.

`V2__add_fecha_creacion.sql` añade la fecha de creación. El listado más habitual es "lo más reciente primero", y para eso hace falta una fecha:

```sql
ALTER TABLE tasks ADD COLUMN fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
```

El esquema cambia con una migración nueva, nunca editando `V1`, que ya se aplicó (lo viste en [Migraciones con Flyway](/docs/02-persistencia/flyway)). El `DEFAULT` da valor a las filas que ya existan: sin él, en una base con datos, el `NOT NULL` haría fallar la migración.

`V3__insert_sample_tasks.sql` inserta 60 tareas repartidas por septiembre de 2026, en orden cronológico, así que el `id` crece con la fecha. Están pensadas para los ejemplos de esta sección: 22 completadas y 38 pendientes, varias con "informe" o "comprar" en el título, una con un `%`, otra con un `_` y cuatro (de la 21 a la 24) creadas exactamente en el mismo instante.

En la entidad, el campo nuevo lo rellena Hibernate al insertar:

```java
// Hibernate la rellena al insertar; updatable = false impide que un UPDATE la cambie
@CreationTimestamp
@Column(nullable = false, updatable = false)
private Instant fechaCreacion;
```

`Instant` es un instante exacto en UTC y encaja con `TIMESTAMP WITH TIME ZONE`. Tiene getter, pero no setter: nadie debería cambiar cuándo se creó una tarea.

## `Pageable`: qué página quieres

`JpaRepository` ya sabe paginar: tiene un `findAll(Pageable pageable)` que devuelve un `Page<Task>`. Un `Pageable` dice qué página se quiere, de qué tamaño y en qué orden, y no hace falta construirlo a mano: si un método del controlador recibe un `Pageable`, Spring lo rellena con los parámetros de la URL.

```java
// Spring construye el Pageable con ?page=, ?size= y ?sort=. @SortDefault y no
// @PageableDefault: este último fija size=10 aunque la configuración diga otra cosa
@GetMapping("/tasks")
public PagedModel<Task> findAll(
        @RequestParam(required = false) Boolean completada,
        @RequestParam(required = false) String q,
        @SortDefault(sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
    // PagedModel da un JSON estable; un Page devuelto tal cual expone su estructura interna
    return new PagedModel<>(taskService.findAll(completada, q, pageable));
}
```

`completada` y `q` son los filtros, que se explican en [Filtros](./filtros). Los parámetros de la paginación son tres:

| Parámetro | Qué es | Por defecto |
|---|---|---|
| `page` | El número de página, **empezando en 0** | `0` |
| `size` | Cuántos elementos por página | `20` |
| `sort` | `campo`, `campo,asc` o `campo,desc`. Se puede repetir para ordenar por varios campos | el de `@SortDefault` |

```bash
curl "http://localhost:8080/tasks?size=3"
curl "http://localhost:8080/tasks?size=3&page=1"
curl "http://localhost:8080/tasks?sort=titulo&size=5"
curl "http://localhost:8080/tasks?sort=completada,desc&sort=titulo"
```

Las comillas no son opcionales: sin ellas, la terminal interpreta el `&` como "ejecuta esto en segundo plano" y corta la URL.

Que las páginas empiecen en 0 sorprende al principio. Si tu cliente prefiere contar desde 1, `spring.data.web.pageable.one-indexed-parameters: true` lo cambia para toda la aplicación.

## La respuesta: `PagedModel`

La primera petición, `?size=3`, devuelve esto:

```json
{
  "content": [
    {
      "titulo": "Planificar el menú de la semana",
      "descripcion": null,
      "completada": false,
      "fechaCreacion": "2026-09-29T17:00:00Z",
      "id": 60
    },
    {
      "titulo": "Practicar la charla del meetup",
      "descripcion": "Cronometrar",
      "completada": false,
      "fechaCreacion": "2026-09-29T09:00:00Z",
      "id": 59
    },
    {
      "titulo": "Ordenar el escritorio",
      "descripcion": null,
      "completada": false,
      "fechaCreacion": "2026-09-28T17:00:00Z",
      "id": 58
    }
  ],
  "page": {
    "size": 3,
    "number": 0,
    "totalElements": 60,
    "totalPages": 20
  }
}
```

`content` trae las tareas de la página, y `page`, lo que necesita un paginador para pintarse: en qué página estás, cuántas tareas hay y cuántas páginas salen.

¿Por qué `new PagedModel<>(...)` y no devolver el `Page` tal cual? Se puede, y funciona, pero el JSON que sale es la estructura interna de `PageImpl`, la clase que usa Spring Data por dentro:

```json
{
  "content": [ "…" ],
  "empty": false,
  "first": true,
  "last": false,
  "number": 0,
  "numberOfElements": 2,
  "pageable": {
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 2,
    "paged": true,
    "sort": { "empty": true, "sorted": false, "unsorted": true },
    "unpaged": false
  },
  "size": 2,
  "sort": { "empty": true, "sorted": false, "unsorted": true },
  "totalElements": 60,
  "totalPages": 30
}
```

Y en el log aparece, la primera vez, este aviso:

```
WARN ... Serializing PageImpl instances as-is is not supported, meaning that there is no guarantee about the stability of the resulting JSON structure!
```

Spring Data avisa de que ese formato no es un contrato: puede cambiar en cualquier versión, y con él cambiaría tu API sin que hubieras tocado una línea. Además repite datos (`size` y `pageable.pageSize`, `number` y `pageable.pageNumber`) y enseña detalles que al cliente no le importan. `PagedModel` es el formato estable que recomienda Spring Data. Si prefieres que todos los `Page` se conviertan solos, la propiedad `spring.data.web.pageable.serialization-mode: via-dto` hace esa conversión en toda la aplicación. Aquí se usa `PagedModel` de forma explícita para que se vea en el código.

## El orden por defecto: `@SortDefault`

Si la URL no trae `sort`, se aplica el de `@SortDefault`: `fechaCreacion` descendente, las más recientes primero.

Existe otra anotación, `@PageableDefault`, que parece más completa porque también fija el tamaño. Cuidado con ella: su atributo `size` vale 10 si no lo indicas, así que `@PageableDefault(sort = "fechaCreacion", direction = DESC)` deja las páginas en 10 elementos aunque `spring.data.web.pageable.default-page-size` diga 20. Con `@SortDefault`, el tamaño sigue saliendo de la configuración.

Y algo que no es evidente: **si el cliente manda su propio `sort`, Spring descarta entero el de la anotación**. `?sort=titulo` ordena solo por título; la fecha ya no participa.

## El desempate: un orden que no cambie entre páginas

Ordenar por `fechaCreacion` parece suficiente, pero las tareas 21 a 24 se crearon en el mismo instante. ¿En qué orden van entre ellas? En ninguno en particular: SQL no promete ningún orden entre filas que empatan en el `ORDER BY`, y la base de datos puede devolverlas de una forma en una consulta y de otra en la siguiente.

Con una sola consulta da igual. Con paginación, no: cada página es una consulta distinta, con su propio `OFFSET`, y si el orden de las empatadas cambia de una a otra, una tarea puede salir en dos páginas y otra no salir en ninguna.

Puedes verlo con la consola de H2, que el perfil `h2` activa en `application-h2.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/tasks;MODE=PostgreSQL
    username: sa
    password:
  h2:
    console:
      # Consola web en /h2-console para lanzar SQL a mano; solo para explorar en local
      enabled: true
```

Arranca con `SPRING_PROFILES_ACTIVE=h2`, abre `http://localhost:8080/h2-console` en el navegador y conéctate con la URL de JDBC que escribe el log al arrancar (`jdbc:h2:file:./data/tasks`), usuario `sa` y contraseña vacía. Después pide las tareas por fecha, de dos en dos, justo donde están las empatadas:

```sql
SELECT id FROM tasks ORDER BY fecha_creacion LIMIT 2 OFFSET 20;
SELECT id FROM tasks ORDER BY fecha_creacion LIMIT 2 OFFSET 22;
```

Son dos páginas seguidas, y entre las dos deberían salir las cuatro tareas empatadas: 21, 22, 23 y 24. En una de las pruebas salió esto:

| Consulta | ids |
|---|---|
| `LIMIT 2 OFFSET 20` | 24, 23 |
| `LIMIT 2 OFFSET 22` | 23, 21 |

La 23 sale dos veces y la 22 no sale nunca. En otra sesión de la consola falló al revés (se repitió la 22 y faltó la 23), y contra PostgreSQL las dos consultas devolvieron las mismas dos tareas, 22 y 21. Si arrancaste con `docker compose`, puedes repetirlo en PostgreSQL desde `psql`: `docker compose exec postgres psql -U tasks tasks`. A ti te puede salir otra combinación, o incluso la correcta: ese es justo el problema, que no está garantizado.

La solución es terminar el orden con un campo que no se repita nunca, el `id`:

```sql
SELECT id FROM tasks ORDER BY fecha_creacion, id LIMIT 2 OFFSET 20;   -- 21, 22
SELECT id FROM tasks ORDER BY fecha_creacion, id LIMIT 2 OFFSET 22;   -- 23, 24
```

En la API, el desempate no puede ir en `@SortDefault`, porque el `sort` del cliente lo sustituiría. Lo añade el servicio a cualquier orden que llegue:

```java
// Si dos tareas empatan en el campo de orden, la base de datos puede devolverlas en
// cualquier orden, y una tarea puede repetirse o perderse entre páginas. El id es
// único, así que añadirlo al final deja un orden estable. Va aquí y no en @SortDefault
// porque, si el cliente manda su propio sort, Spring descarta el de la anotación entero.
private Pageable conDesempate(Pageable pageable) {
    Sort sort = pageable.getSort();
    if (sort.getOrderFor("id") == null) {
        sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
    }
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
}
```

La dirección del desempate da igual para la estabilidad, porque lo que importa es que el `id` es único. `DESC` mantiene "lo más nuevo primero" entre tareas empatadas. Si el cliente ya ordena por `id`, no se añade otra vez. Este es el SQL que lanza Hibernate para la primera página:

```sql
select t1_0.id,t1_0.completada,t1_0.descripcion,t1_0.fecha_creacion,t1_0.titulo from tasks t1_0 order by t1_0.fecha_creacion desc,t1_0.id desc offset ? rows fetch first ? rows only
```

:::tip[Índices]
Con 60 filas, ordenar la tabla entera es instantáneo. Con millones, "las 20 más recientes" solo es rápido si hay un índice con el mismo orden (`fecha_creacion DESC, id DESC`): la base de datos lee 20 entradas del índice en vez de ordenar toda la tabla. El ejemplo no lo crea porque no lo necesita.
:::

:::note[El orden alfabético depende de la base de datos]
`?sort=titulo` deja el orden en manos de la *collation* de la base de datos, y no todas ordenan igual. H2, y una base de PostgreSQL creada con la configuración regional `C`, comparan por el código de cada carácter: las mayúsculas van antes que las minúsculas, y las letras con tilde, detrás de la `z`. Por eso, con el perfil `h2`, "Añadir paginación al listado de tareas" sale detrás de "Arreglar la bici". La imagen oficial de PostgreSQL que usa `docker compose` (`en_US.utf8`) ordena como un diccionario, sin separar mayúsculas y minúsculas. La misma petición puede dar otro orden con `docker compose` que con el perfil `h2`.
:::
