# 02-persistencia

API REST de gestión de tareas — ejemplo ejecutable de la Fase 2 (Persistencia) del sitio **Spring Boot desde cero**. Mismo dominio que `01-fundamentos`, pero ahora con Spring Data JPA, PostgreSQL y migraciones versionadas con Flyway en vez de un repositorio en memoria.

## Requisitos

- JDK 21 o superior.
- Docker (para levantar Postgres) — **opcional**, ver [Sin Docker](#sin-docker-perfil-h2) más abajo si no lo tienes instalado.

## Levantar la base de datos

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5433` (usuario/contraseña/base de datos: `tasks`).

## Ejecutar

```bash
./mvnw spring-boot:run
```

Flyway crea el esquema automáticamente al arrancar.

## Sin Docker (perfil `h2`)

¿No tienes Docker instalado o no quieres levantar un contenedor solo para probar esto? El perfil `h2` ejecuta la app contra una base H2 guardada en un archivo local (`data/tasks.mv.db`, ya en `.gitignore`) en vez de Postgres — mismas migraciones de Flyway, mismo esquema, y los datos sobreviven a un reinicio igual que con Postgres real:

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

Esto es distinto del H2 que usan los tests (`src/test/resources/application.yml`): ese es en memoria y se borra en cada ejecución de `./mvnw test`; este otro (`src/main/resources/application-h2.yml`) escribe a disco para que puedas parar la app, volver a arrancarla, y seguir viendo tus datos. Sirve para explorar el API sin instalar nada más que el JDK — no sustituye probar contra Postgres real si quieres confirmar que todo funciona igual en el motor de base de datos "de verdad".

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/tasks` | Crear una tarea |
| GET | `/tasks` | Listar todas las tareas |
| GET | `/tasks/{id}` | Obtener una tarea |
| PUT | `/tasks/{id}` | Actualizar una tarea |
| DELETE | `/tasks/{id}` | Eliminar una tarea |

## Tests

```bash
./mvnw test
```

Usan H2 en memoria (ver `src/test/resources/application.yml`) — no requieren Docker ni Postgres.
