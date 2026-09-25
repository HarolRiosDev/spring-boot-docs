# 04-cache-redis

API REST de gestión de tareas — ejemplo ejecutable de la Fase 4 (Caché/Redis) del sitio **Spring Boot desde cero**. Mismo dominio y mismo API que `03-security-jwt` (auth JWT, roles, ownership) — el API público es idéntico: cachear no le añade ni le quita ninguna ruta. La diferencia vive por debajo: las lecturas por id pasan primero por una caché (Redis en producción, memoria en desarrollo/tests).

## Requisitos

- JDK 21 o superior.
- Docker (para levantar Postgres y Redis) — **opcional**, ver [Sin Docker](#sin-docker-perfil-h2) más abajo si no lo tienes instalado.

## Levantar la base de datos y Redis

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5435` (usuario/contraseña/base de datos: `tasks_cache`) y Redis en `localhost:6379` (puerto por defecto, sin autenticación).

## Ejecutar

```bash
./mvnw spring-boot:run
```

Flyway crea el esquema y siembra un usuario administrador al arrancar:

- **username:** `admin`
- **password:** `admin12345`

## Sin Docker (perfil `h2`)

¿No tienes Docker instalado? El perfil `h2` ejecuta la app contra H2 en archivo (`data/tasks_cache.mv.db`, en `.gitignore`) y fuerza `ConcurrentMapCacheManager` en vez de `RedisCacheManager` — cero Postgres, cero Redis, mismas anotaciones `@Cacheable`/`@CacheEvict` funcionando de verdad contra una caché real en memoria:

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

Esto es distinto del H2 que usan los tests (`src/test/resources/application.yml`, en memoria, se borra en cada `./mvnw test`); este otro (`src/main/resources/application-h2.yml`) escribe a disco para que puedas explorar el API completo con solo el JDK instalado — no sustituye probar contra Postgres/Redis reales.

## Endpoints

Idénticos a la Fase 3 — cachear es invisible desde fuera del API.

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

Las rutas protegidas requieren el header `Authorization: Bearer <token>` obtenido de `/auth/login`.

## Verificar la caché manualmente (con Redis real)

Con `docker compose up -d` corriendo (`redis-cli` se ejecuta dentro del contenedor, no hace falta instalarlo):

```bash
docker compose exec redis redis-cli keys "tasks::*"
docker compose exec redis redis-cli get "tasks::1"
```

Tras un `GET /tasks/1`, debería aparecer una clave `tasks::1` con el JSON de la tarea. Tras un `PUT`/`DELETE` sobre esa misma tarea, la clave desaparece.

## Tests

```bash
./mvnw test
```

Usan H2 en memoria + `spring.cache.type: simple` (ver `src/test/resources/application.yml`) — no requieren Docker, Postgres ni Redis. Los tests obtienen JWT reales registrando/logueando usuarios contra el propio API, sin autenticación simulada, y verifican la caché contra el `CacheManager` real de Spring, no contra un mock.
