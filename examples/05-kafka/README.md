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

Corre la app contra H2 en archivo (`data/tasks_kafka.mv.db`, en `.gitignore`) — a diferencia de Postgres, Kafka no tiene un equivalente para correr sin Docker fuera de tests. Sin un Kafka real en `localhost:9092`, la app arranca y `POST /tasks`/`PUT /tasks/{id}` siguen respondiendo con normalidad, pero no de forma instantánea ni asíncrona: el envío del evento a Kafka ocurre de forma síncrona, en el mismo hilo que atiende la petición HTTP, justo después de que la transacción confirma (`AFTER_COMMIT`), y `KafkaConfig` acota ese envío a 3 segundos vía `max.block.ms`. Sin Kafka disponible, cada `POST /tasks`/`PUT /tasks/{id}` que publica un evento tarda hasta ~3 segundos extra y registra un error en el log — pero siempre termina respondiendo, nunca se queda colgada indefinidamente. Ver [productores-y-eventos.md](../../docs-site/docs/05-kafka/productores-y-eventos.md) para la explicación completa.

Caché: este ejemplo usa `ConcurrentMapCacheManager` (`spring.cache.type: simple`) — esta fase es sobre Kafka, no sobre caché. El `CacheConfig` con Redis se conserva tal cual vino del port de la Fase 4, pero nunca se activa aquí: `docker-compose.yml` no levanta ningún servicio de Redis.

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

## Pendiente de verificación

- `docker-compose.yml` nunca se levantó de verdad en el entorno donde se desarrolló este ejemplo (sin Docker disponible) — Postgres y Kafka reales quedan sin probar end-to-end.
- La columna `created_at TIMESTAMP` de `notifications` (mapeada desde `Instant` en `Notification.createdAt`) pasa `ddl-auto: validate` contra H2, pero no se verificó contra un Postgres real — Hibernate 6 normalmente prefiere `TIMESTAMPTZ`/`timestamp with time zone` para `Instant`, y aunque es muy probable que el validador de esquema de Hibernate también la acepte ahí por un match de prefijo, queda sin confirmar.
