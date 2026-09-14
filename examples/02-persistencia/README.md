# 02-persistencia

API REST de gestión de tareas — ejemplo ejecutable de la Fase 2 (Persistencia) del sitio **Spring Boot desde cero**. Mismo dominio que `01-fundamentos`, pero ahora con Spring Data JPA, PostgreSQL y migraciones versionadas con Flyway en vez de un repositorio en memoria.

## Requisitos

- JDK 21 o superior.
- Docker (para levantar Postgres). Los tests NO necesitan Docker — usan H2 en memoria automáticamente.

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
