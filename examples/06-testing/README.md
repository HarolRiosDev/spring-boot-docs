# 06-testing

API REST de gestión de tareas — ejemplo ejecutable de la Fase 6 (Testing) del sitio **Spring Boot desde cero**. Mismo dominio y mismo API que `04-cache-redis` (auth JWT, roles, ownership, caché) — el API público es idéntico. El foco de esta fase no es el API sino cómo se prueba: tests parametrizados (JUnit), tests unitarios puros (Mockito) y tests de integración contra infraestructura real (Testcontainers).

## Requisitos

- JDK 21 o superior.
- Docker — necesario para `docker compose up` (ejecutar la app manualmente) **y** para los tests `*IT.java` de Testcontainers (`./mvnw verify`). Ver [Sin Docker](#sin-docker-perfil-h2) si no lo tienes instalado.

## Levantar la base de datos y Redis

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5436` (usuario/contraseña/base de datos: `tasks_testing`) y Redis en `localhost:6379`.

## Ejecutar

```bash
./mvnw spring-boot:run
```

Flyway crea el esquema y siembra un usuario administrador al arrancar:

- **username:** `admin`
- **password:** `admin12345`

## Sin Docker (perfil `h2`)

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

Corre la app contra H2 en archivo (`data/tasks_testing.mv.db`, en `.gitignore`) y fuerza `ConcurrentMapCacheManager` en vez de `RedisCacheManager` — cero Postgres, cero Redis.

## Endpoints

Idénticos a la Fase 4.

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/auth/register` | pública | Registrar un usuario nuevo (rol `USER`) |
| POST | `/auth/login` | pública | Login, devuelve un JWT |
| POST | `/tasks` | JWT | Crear una tarea (propia) |
| GET | `/tasks` | JWT | Listar tareas (propias si `USER`, todas si `ADMIN`) — no cacheado |
| GET | `/tasks/{id}` | JWT | Obtener una tarea (propia, o cualquiera si `ADMIN`) — cacheado |
| PUT | `/tasks/{id}` | JWT | Actualizar una tarea (propia, o cualquiera si `ADMIN`) — invalida la caché |
| DELETE | `/tasks/{id}` | JWT | Eliminar una tarea (propia, o cualquiera si `ADMIN`) — invalida la caché |
| GET | `/admin/users` | JWT + rol `ADMIN` | Listar todos los usuarios |

## Tests

```bash
./mvnw test
```

Tests unitarios (`TaskServiceImplTest`, con Mockito, sin Spring ni base de datos) y de integración con H2 + `spring.cache.type: simple` — no requieren Docker.

```bash
./mvnw verify
```

Añade `TaskApiIT`, que levanta Postgres y Redis reales vía Testcontainers — **requiere Docker corriendo**. Sin Docker, usar `./mvnw verify -DskipITs` para compilar y empaquetar sin ejecutar ese test.
