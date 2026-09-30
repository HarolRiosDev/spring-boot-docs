# 02b-bd-existente

API REST de gestión de tareas contra una **base de datos que ya existía**: ejemplo ejecutable de la sección 2b (Base de datos existente) del sitio **Spring Boot desde cero**. El esquema no lo crea la aplicación: lo "heredó" de otro equipo, con nombres como `TB_TAREA` o `DS_TITULO`, banderas `'S'`/`'N'`, una secuencia, un trigger, una vista y una función en PL/pgSQL. La aplicación se adapta a él.

## Requisitos

- JDK 21 o superior.
- Docker, **también para los tests de integración**: sin la base de datos heredada no hay nada que mapear, así que este ejemplo no tiene perfil `h2`.

## Qué simula

`docker/legacy-schema.sql` es el esquema "del DBA". Lo ejecuta Postgres la primera vez que arranca el contenedor (`/docker-entrypoint-initdb.d/`), nunca la aplicación:

| Objeto | Qué es |
|---|---|
| `TB_TAREA` | Tareas, con `FL_COMPLETADA` como `CHAR(1)` `'S'`/`'N'` y fechas que rellena la propia base de datos |
| `SQ_TAREA` | Secuencia de ids: empieza en 1000 y avanza de uno en uno |
| `TB_COMENTARIO_TAREA` | Comentarios, con clave primaria compuesta `(ID_TAREA, NU_LINEA)` |
| `TR_TAREA_MODIFICADA` | Trigger que pone `FH_MODIFICACION` en cada `UPDATE` |
| `VW_RESUMEN_TAREA` | Vista con el número de comentarios de cada tarea |
| `FN_PURGAR_COMPLETADAS` | Función que borra las tareas completadas antiguas y devuelve cuántas borró |

Además trae datos de ejemplo (tareas 1000 a 1005). La aplicación adopta ese esquema con Flyway (`baseline-on-migrate`) y solo añade sus propios cambios, a partir de `src/main/resources/db/migration/V2__control_de_concurrencia.sql`.

## Arrancar

```bash
docker compose up -d
./mvnw spring-boot:run
```

Postgres queda en `localhost:5439` (base de datos, usuario y contraseña: `legacy`). El script del DBA solo se ejecuta cuando el volumen de datos está vacío: para volver al esquema original, `docker compose down -v` y otra vez `docker compose up -d`.

Sin `docker compose`, `./mvnw spring-boot:test-run` arranca la aplicación contra un Postgres efímero de Testcontainers, con el mismo script, que desaparece al pararla.

## Mirar el esquema

```bash
docker compose exec postgres psql -U legacy legacy
```

Dentro de `psql`: `\d tb_tarea`, `\d+ vw_resumen_tarea` o `select * from flyway_schema_history;`.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/tasks` | Crear una tarea |
| GET | `/tasks` | Listar todas las tareas |
| GET | `/tasks/{id}` | Obtener una tarea |
| PUT | `/tasks/{id}` | Actualizar una tarea, enviando la `version` que se leyó |
| DELETE | `/tasks/{id}` | Borrar una tarea y sus comentarios |
| GET | `/tasks/{id}/comments` | Comentarios de una tarea |
| POST | `/tasks/{id}/comments` | Añadir un comentario |
| GET | `/tasks/summary` | Resumen de cada tarea, leído de la vista `VW_RESUMEN_TAREA` |
| GET | `/reports/tasks-per-day` | Tareas creadas y completadas por día (SQL con `JdbcClient`) |
| POST | `/tasks/purge?days=30` | Borrar las completadas de hace más de N días (función de la base de datos) |

```bash
curl -s -X POST localhost:8080/tasks -H 'Content-Type: application/json' \
  -d '{"titulo":"Probar el ejemplo","descripcion":"desde curl","completada":false}'
curl -s localhost:8080/tasks/1003/comments
curl -s localhost:8080/tasks/summary
curl -s localhost:8080/reports/tasks-per-day
curl -s -X POST 'localhost:8080/tasks/purge?days=30'
```

`completada` va siempre en el JSON: es un `boolean` y, si falta, Jackson rechaza la petición con un 400.

## Otra aplicación en la misma tabla: el 409

La tarea 1004 empieza en la versión 0. Simula que otra aplicación la cambia por SQL, sin saber que `NU_VERSION` existe:

```bash
docker compose exec postgres psql -U legacy legacy \
  -c "UPDATE TB_TAREA SET DS_TITULO = 'Cambiada por la otra aplicación' WHERE ID_TAREA = 1004"
```

El trigger ha subido la versión a 1. Un `PUT` con la versión que habías leído (0) se rechaza en vez de pisar el cambio:

```bash
curl -s -X PUT localhost:8080/tasks/1004 -H 'Content-Type: application/json' \
  -d '{"titulo":"Mi cambio","completada":false,"version":0}'
```

```json
{"status":409,"message":"La tarea 1004 cambió desde que la leíste (leíste la versión 0, la actual es la 1); vuelve a leerla antes de modificarla","timestamp":"..."}
```

## Tests

```bash
./mvnw test     # solo SiNoConverterTest: no necesita Docker
./mvnw verify   # todos: los *IT levantan un Postgres con Testcontainers
```

Los tests de integración (`*IT`) arrancan un contenedor `postgres:16` con el mismo `docker/legacy-schema.sql` que usa `docker-compose.yml`: prueban contra el esquema real, no contra una copia en H2.
