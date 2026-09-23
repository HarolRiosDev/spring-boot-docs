# 05-kafka

API REST de gestión de tareas — ejemplo ejecutable de la Fase 5 (Mensajería con Kafka) del sitio **Spring Boot desde cero**. Mismo dominio y mismo API que `04-cache-redis` (auth JWT, roles, ownership, caché) — el endpoint público es idéntico salvo por un endpoint nuevo. La diferencia vive por debajo: crear o completar una tarea publica un evento a Kafka; un consumidor separado lo procesa y guarda una notificación.

## Requisitos

- JDK 21 o superior.
- Docker (para levantar Postgres y Kafka) — **opcional**, ver [Sin Docker](#sin-docker-perfil-h2) más abajo.

## Levantar la base de datos y Kafka

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5437` (usuario/contraseña/base de datos: `tasks_kafka`) y Kafka en `localhost:9092` (modo KRaft, sin Zookeeper, un único nodo).

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

Corre la app contra H2 en archivo (`data/tasks_kafka.mv.db`, en `.gitignore`) — a diferencia de Postgres, Kafka no tiene un equivalente para correr sin Docker fuera de tests. Sin un Kafka real en `localhost:9092`, la app arranca y `POST /tasks`/`PUT /tasks/{id}` siguen respondiendo con normalidad — el envío del evento falla de forma asíncrona y queda logueado como error, sin bloquear la respuesta HTTP.

## Endpoints

Idénticos a la Fase 4, más uno nuevo.

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/auth/register` | pública | Registrar un usuario nuevo (rol `USER`) |
| POST | `/auth/login` | pública | Login, devuelve un JWT |
| POST | `/tasks` | JWT | Crear una tarea (propia) — publica un evento `CREATED` |
| GET | `/tasks` | JWT | Listar tareas (propias si `USER`, todas si `ADMIN`) |
| GET | `/tasks/{id}` | JWT | Obtener una tarea (propia, o cualquiera si `ADMIN`) — cacheado |
| PUT | `/tasks/{id}` | JWT | Actualizar una tarea (propia, o cualquiera si `ADMIN`) — publica un evento `COMPLETED` si `completada` pasa de `false` a `true` |
| DELETE | `/tasks/{id}` | JWT | Eliminar una tarea (propia, o cualquiera si `ADMIN`) |
| GET | `/notifications` | JWT | Listar notificaciones (propias si `USER`, todas si `ADMIN`) |
| GET | `/admin/users` | JWT + rol `ADMIN` | Listar todos los usuarios |

## Verificar Kafka manualmente (con Kafka real)

Con `docker compose up -d` corriendo:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic task-events --from-beginning
```

Tras un `POST /tasks`, debería aparecer un mensaje JSON con el evento `CREATED`.

## Tests

```bash
./mvnw test
```

Usan H2 en memoria + `EmbeddedKafkaBroker` (un broker Kafka real embebido en el proceso de test, no un mock) — no requieren Docker. Los tests obtienen JWT reales registrando/logueando usuarios contra el propio API.
