# 02c-paginacion

API REST de gestión de tareas — ejemplo ejecutable de la sección 2c (Paginación) del sitio **Spring Boot desde cero**. Parte de `02-persistencia` y añade lo necesario para servir el listado por páginas: `GET /tasks` paginado y ordenado de forma estable, filtros opcionales, límites para que nadie pida más de la cuenta y un listado "cargar más" con `Slice`.

## Requisitos

- JDK 21 o superior.
- Docker (para levantar Postgres) — **opcional**, ver [Sin Docker](#sin-docker-perfil-h2) más abajo.

## Levantar la base de datos

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5440` (usuario/contraseña/base de datos: `tasks`).

## Ejecutar

```bash
./mvnw spring-boot:run
```

Flyway crea el esquema y carga **60 tareas de ejemplo** al arrancar (migraciones `V1` a `V3`), así que el listado tiene varias páginas desde el principio.

## Sin Docker (perfil `h2`)

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

La app usa una base H2 guardada en un archivo local (`data/tasks.mv.db`, ya en `.gitignore`), con las mismas migraciones y las mismas 60 tareas. Este perfil activa además la **consola de H2** en <http://localhost:8080/h2-console>: conéctate con la URL de JDBC que escribe el log al arrancar (`jdbc:h2:file:./data/tasks`), usuario `sa` y contraseña vacía, y podrás lanzar SQL a mano contra la base de la aplicación.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/tasks` | Una página de tareas (ver parámetros abajo) |
| GET | `/tasks/recientes` | Las más recientes, para un "cargar más": sin totales, solo `hasNext` |
| POST | `/tasks` | Crear una tarea |
| GET | `/tasks/{id}` | Obtener una tarea |
| PUT | `/tasks/{id}` | Actualizar una tarea |
| DELETE | `/tasks/{id}` | Eliminar una tarea |

Parámetros de `GET /tasks`, todos opcionales:

| Parámetro | Valores | Por defecto |
|---|---|---|
| `page` | Número de página, empieza en 0 | `0` |
| `size` | Tamaño de página; lo que pase de 100 se recorta a 100 | `20` |
| `sort` | `campo`, `campo,asc` o `campo,desc` (se puede repetir). Campos: `fechaCreacion`, `titulo`, `completada`, `id` | `fechaCreacion,desc` (desempate abajo) |
| `completada` | `true` o `false` | sin filtro |
| `q` | Texto que debe aparecer en el título, sin distinguir mayúsculas | sin filtro |

Si el `sort` no incluye `id`, se añade siempre `id,desc` al final como desempate: si dos tareas empatan en el campo de orden, sin él una podría repetirse o perderse entre páginas. Así, el orden por defecto es en realidad `fechaCreacion,desc` + `id,desc`.

`GET /tasks/recientes` acepta `page` y `size`; el orden es siempre el de las más recientes (`fechaCreacion,desc` + `id,desc`).

## Probar con `curl`

Las comillas son necesarias: sin ellas, la terminal corta la URL en el `&`.

```bash
curl "http://localhost:8080/tasks?size=3"                       # primera página de 3
curl "http://localhost:8080/tasks?size=3&page=1"                # la siguiente
curl "http://localhost:8080/tasks?sort=titulo&size=5"           # por título
curl "http://localhost:8080/tasks?completada=false&q=informe"   # pendientes con "informe"
curl "http://localhost:8080/tasks?size=5000"                    # recortado a 100
curl "http://localhost:8080/tasks?sort=noexiste"                # 400 con ApiError
curl "http://localhost:8080/tasks/recientes?size=3"             # Slice: hasNext, sin totales
```

## Ver el SQL

Para ver las consultas que lanza Hibernate (la de la página y, en `GET /tasks`, la del `COUNT`), añade temporalmente a `src/main/resources/application.yml`:

```yaml
logging:
  level:
    org.hibernate.SQL: debug
```

## Tests

```bash
./mvnw test
```

Usan H2 en memoria (ver `src/test/resources/application.yml`) — no requieren Docker ni Postgres.
