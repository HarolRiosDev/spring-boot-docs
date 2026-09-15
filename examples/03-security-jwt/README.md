# 03-security-jwt

API REST de gestión de tareas — ejemplo ejecutable de la Fase 3 (Security/JWT) del sitio **Spring Boot desde cero**. Mismo dominio que `01-fundamentos`/`02-persistencia`, ahora protegido con autenticación JWT propia, roles y ownership de tareas.

## Requisitos

- JDK 21 o superior.
- Docker (para levantar Postgres). Los tests NO necesitan Docker — usan H2 en memoria automáticamente.

## Levantar la base de datos

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5434` (usuario/contraseña/base de datos: `tasks_security`).

## Ejecutar

```bash
./mvnw spring-boot:run
```

Flyway crea el esquema y siembra un usuario administrador al arrancar:

- **username:** `admin`
- **password:** `admin12345`

## Endpoints

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/auth/register` | pública | Registrar un usuario nuevo (rol `USER`) |
| POST | `/auth/login` | pública | Login, devuelve un JWT |
| POST | `/tasks` | JWT | Crear una tarea (propia) |
| GET | `/tasks` | JWT | Listar tareas (propias si `USER`, todas si `ADMIN`) |
| GET | `/tasks/{id}` | JWT | Obtener una tarea (propia, o cualquiera si `ADMIN`) |
| PUT | `/tasks/{id}` | JWT | Actualizar una tarea (propia, o cualquiera si `ADMIN`) |
| DELETE | `/tasks/{id}` | JWT | Eliminar una tarea (propia, o cualquiera si `ADMIN`) |
| GET | `/admin/users` | JWT + rol `ADMIN` | Listar todos los usuarios |

Las rutas protegidas requieren el header `Authorization: Bearer <token>` obtenido de `/auth/login`.

## Tests

```bash
./mvnw test
```

Usan H2 en memoria (ver `src/test/resources/application.yml`) — no requieren Docker ni Postgres. Los tests obtienen JWT reales registrando/logueando usuarios contra el propio API, sin autenticación simulada.
