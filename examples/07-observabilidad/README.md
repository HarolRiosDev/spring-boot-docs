# 07-observabilidad

API REST de gestión de tareas: ejemplo ejecutable de la Fase 7 (Producción) del sitio **Spring Boot desde cero**. Mismo dominio y mismo API que `06-testing` (auth JWT, roles, ownership, caché). Lo nuevo es lo que la app necesita para vivir en producción: Actuator en un puerto de gestión separado, métricas con Micrometer y Prometheus (con un dashboard de Grafana), logs estructurados con `traceId`, la cabecera `X-Trace-Id` y la documentación OpenAPI generada con springdoc.

## Requisitos

- JDK 21 o superior.
- Docker, para `docker compose up` (Postgres, Redis, Prometheus y Grafana) y para `TaskApiIT` (`./mvnw verify`). Si no lo tienes, mira [Sin Docker](#sin-docker-perfil-h2).

## Arrancar

```bash
docker compose up -d
./mvnw spring-boot:run
```

Flyway crea el esquema y siembra un usuario administrador: `admin` / `admin12345`.

## Puertos

| Puerto | Qué |
|---|---|
| 8080 | API |
| 8081 | Actuator (puerto de gestión: en producción no se publica hacia fuera) |
| 5438 | PostgreSQL (usuario, contraseña y base de datos: `tasks_observability`) |
| 6379 | Redis |
| 9090 | Prometheus |
| 3001 | Grafana (el 3000 lo usa Docusaurus en desarrollo) |

## URLs útiles

| URL | Qué muestra |
|---|---|
| http://localhost:8080/swagger-ui.html | Swagger UI. Pide un token en `POST /auth/login` y pégalo en **Authorize** |
| http://localhost:8080/v3/api-docs | La especificación OpenAPI en JSON |
| http://localhost:8081/actuator/health | Estado de la app (con un token de ADMIN, con el detalle de cada componente) |
| http://localhost:8081/actuator/health/liveness y `/readiness` | Las dos probes |
| http://localhost:8081/actuator/info | Versión de la app |
| http://localhost:8081/actuator/prometheus | Todas las métricas en formato Prometheus |
| http://localhost:9090/targets | Prometheus: el target `tasks-api` debe aparecer `UP` |
| http://localhost:3001 | Grafana, con el dashboard "Tasks API" como página de inicio (sin login, solo lectura) |

`/actuator/metrics` pide un token de ADMIN.

## Logs en JSON (perfil `prod`)

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Cada línea de log es un objeto JSON (formato ECS) con `traceId` y `spanId`. El perfil `prod` también apaga Swagger UI (el JSON de `/v3/api-docs` sigue disponible) y oculta el detalle de `health`.

Cada respuesta del API (puerto 8080) lleva la cabecera `X-Trace-Id`. Con ese valor se encuentran en los logs todas las líneas de esa petición.

## Sin Docker (perfil `h2`)

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

Usa H2 en archivo (`data/tasks_observability.mv.db`, en `.gitignore`) y la caché en memoria: sin Postgres ni Redis. Actuator (8081) y Swagger UI funcionan igual; Prometheus y Grafana no, porque viven en el `docker-compose.yml`. Para ver los logs en JSON sin Docker: `SPRING_PROFILES_ACTIVE=h2,prod`.

## Endpoints

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/auth/register` | pública | Registrar un usuario nuevo (rol `USER`) |
| POST | `/auth/login` | pública | Login, devuelve un JWT |
| POST | `/tasks` | JWT | Crear una tarea (propia) |
| GET | `/tasks` | JWT | Listar tareas (propias si `USER`, todas si `ADMIN`); no cacheado |
| GET | `/tasks/{id}` | JWT | Obtener una tarea (propia, o cualquiera si `ADMIN`); cacheado |
| PUT | `/tasks/{id}` | JWT | Actualizar una tarea (propia, o cualquiera si `ADMIN`); invalida la caché |
| DELETE | `/tasks/{id}` | JWT | Eliminar una tarea (propia, o cualquiera si `ADMIN`); invalida la caché |
| GET | `/admin/users` | JWT + rol `ADMIN` | Listar todos los usuarios |

## Tests

```bash
./mvnw test
```

Sin Docker: H2 y caché en memoria. Incluyen Actuator en su propio puerto, las métricas, la cabecera `X-Trace-Id`, los logs en JSON del perfil `prod`, la especificación OpenAPI y que cada métrica del dashboard de Grafana existe en la app.

```bash
./mvnw verify
```

Añade `TaskApiIT`, que levanta Postgres y Redis reales con Testcontainers: **necesita Docker**. Sin Docker, `./mvnw verify -DskipITs` compila y empaqueta sin ejecutar ese test.

## Qué cubre el CI y qué no

El CI ejecuta `./mvnw verify`: los tests con H2 y `TaskApiIT` contra Postgres y Redis reales, incluido que las estadísticas de la caché de Redis llegan a `/actuator/prometheus`. No levanta el `docker-compose.yml`: no hay ningún test que arranque Prometheus y Grafana. Lo que sí está comprobado es que cada métrica que consulta el dashboard existe en la app (`GrafanaDashboardMetricsTest`); un nombre mal escrito dejaría un panel vacío sin ningún error. Si al levantar el stack algo no funciona como describe este README, abre un issue.
