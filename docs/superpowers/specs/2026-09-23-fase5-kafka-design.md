# Fase 5 — Mensajería con Kafka (diseño)

> Estado: **diseño aprobado por el usuario el 2026-09-23**. Siguiente paso: invocar `writing-plans` para el plan de implementación.

## Contexto

Continuación del roadmap tras la Fase 4 (Caché y Redis, completa) y la Fase 6 (Testing, completa y mergeada — implementada antes que esta por decisión explícita del usuario, ver [[project-fases-completas]]). El roadmap público del sitio no se renumera (`05-kafka` sigue en `position: 7`); esta es simplemente la siguiente fase a construir.

Decisiones ya cerradas que aplican aquí (ver [[spring-boot-tech-decisions]]): Docusaurus, contenido en español, ejemplos ejecutables autocontenidos en Maven + Java 21, un ejemplo por fase en `examples/`, CI vía GitHub Actions con matriz.

**Lecciones técnicas heredadas que aplican directamente:**
1. No fijar la versión de Spring Boot al generar el ejemplo.
2. Verificar el bit de ejecución de `mvnw` antes de cada commit.
3. Limpiar el `pom.xml` generado por Initializr.
4. Verificar paquetes/artefactos reales contra el proyecto generado, no asumir — aplicado ya en el diseño de esta fase (ver "Verificado empíricamente" más abajo).
5. `examples/05-kafka` es un proyecto Maven **completamente independiente**, no una modificación de `examples/04-cache-redis`.
6. **Revisión final de todo el branch obligatoria** — en Fases 3, 4 y 6 atrapó problemas reales invisibles por-task; en Fase 6 concretamente atrapó un bug entre dos tasks que **nunca tocaron el mismo archivo en su diff** (perfil de test vs. test que lo heredaba) — el tipo de interacción asíncrona/indirecta que esta fase, por su propia naturaleza (productor y consumidor desacoplados), puede volver a producir.
7. Mapeo a DTO siempre dentro del `@Transactional` del service, nunca en el controller.
8. **Cuidado con ejecutar pasos de scaffolding con rutas absolutas hardcodeadas al repo principal cuando el plan se ejecuta en un worktree** — causó un directorio huérfano que bloqueó el merge de la Fase 6. El plan de esta fase debe usar rutas relativas al directorio de trabajo del subagente.
9. **Si el entorno de test difiere del de producción, ningún test por-task puede detectar bugs que solo existen en la ruta de producción** (Fase 4: `ConcurrentMapCacheManager` vs Redis real; Fase 6: perfil de caché heredado silenciosamente). Aquí el equivalente es `EmbeddedKafkaBroker` (test) vs. un clúster Kafka real (producción) — mitigación: revisión de código deliberada contra la documentación real de Spring Kafka en vez de asumir paridad de comportamiento.

**Verificado empíricamente al escribir este spec (no asumido):** el dependency id de Spring Initializr es `kafka` (grupo "Messaging"), que genera `spring-boot-starter-kafka` (compile) + `spring-boot-starter-kafka-test` (test) — confirmado generando un proyecto de prueba real contra start.spring.io. El árbol de dependencias resuelto (`mvn dependency:tree`) confirma `spring-kafka:4.1.1` + `kafka-clients:4.2.1` en compile, y `spring-kafka-test:4.1.1` (que trae `EmbeddedKafkaBroker`) en test. **Kafka 4.x eliminó Zookeeper por completo** (modo KRaft es el único soportado desde Kafka 4.0) — el `docker-compose.yml` de esta fase usa un único servicio Kafka en modo KRaft combinado (broker+controller), no el patrón antiguo de dos contenedores (Zookeeper + Kafka) que pueda aparecer en tutoriales viejos.

## Continuidad pedagógica

**Mismo dominio que las Fases 1-4/6:** gestor de tareas con auth JWT y caché, portado verbatim desde Fase 4 (misma base que usó Fase 6). El foco de esta fase es la mensajería asíncrona: cuando se crea o se completa una tarea, se publica un evento a Kafka; un consumidor separado lo procesa y guarda una notificación consultable.

**Requisito pedagógico explícito del usuario:** el código y las páginas deben ser entendibles **al leerlos o revisarlos una sola vez** — sin tener que cruzar referencias entre archivos para reconstruir el flujo. Esto pesa especialmente en una fase de mensajería asíncrona, donde productor y consumidor están desacoplados por diseño (ese es justo el motivo de la lección técnica 6 de arriba). Consecuencias concretas de diseño:
- Nombres explícitos y sin ambigüedad: `TaskEvent`, `TaskEventPublisher`, `TaskEventListener` (o equivalente) — nunca nombres genéricos como `Handler`/`Processor` que obliguen a abrir el archivo para saber qué hace.
- El punto donde se decide "esto es un evento `CREATED`" o "esto es un evento `COMPLETED`" debe estar en un único lugar, con un comentario que explique la regla (ver "Regla de disparo de `COMPLETED`" más abajo) — no repartido implícitamente entre varios métodos.
- La página de testing (página 3) debe explicar explícitamente *por qué* un test de un flujo async no puede usar las mismas aserciones síncronas ya conocidas de las fases anteriores, antes de mostrar el código de la solución (mismo principio que ya se aplicó a la página de Mockito en la Fase 6: contexto antes que mecánica).

`examples/05-kafka` es un proyecto Maven **independiente y autocontenido**, no una modificación de `examples/04-cache-redis`.

## Estructura de contenido

`docs-site/docs/05-kafka/` (el `_category_.json` con `position: 7` ya existe de la Fase 0, no se toca):

- `index.md` — deja de ser el placeholder "en construcción"; introduce la fase con el contraste "hasta ahora, cada acción del API terminaba en la respuesta HTTP; aquí una acción además dispara algo que pasa después, en otro proceso, sin que quien hizo la petición tenga que esperarlo", y enlaza a las 4 sub-páginas.
- 4 páginas, una por subtema:
  1. **Productores y eventos** — `KafkaTemplate`, el topic único `task-events`, el record `TaskEvent`. Explica por qué la publicación pasa por `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` en vez de llamar a `kafkaTemplate.send(...)` directamente dentro de `TaskServiceImpl` — si la transacción de base de datos hace rollback después de haber publicado el evento, un consumidor ya habría reaccionado a algo que nunca ocurrió de verdad. Mismo tipo de lección que `@CacheEvict` en Fase 4, aplicada aquí a mensajería en vez de caché.
  2. **Consumidores** — `@KafkaListener`, deserialización JSON del `TaskEvent`, escribir en la tabla `notifications`. Qué pasa con un mensaje mal formado (se loguea y se descarta — sin dead-letter queue, fuera de alcance de esta fase, pero se nombra explícitamente por qué).
  3. **Testing con Kafka** — `EmbeddedKafkaBroker`/`@EmbeddedKafka`, y por qué un test de productor→consumidor no puede usar una aserción inmediata después de publicar (el consumidor procesa en otro hilo, en su propio tiempo) — hay que esperar la condición real (polling con `Awaitility` o un `CountDownLatch`), nunca un `Thread.sleep` fijo.
  4. **Probar con Kafka real** — endpoint `GET /notifications`, `docker-compose.yml` con Kafka en modo KRaft, verificación manual con `kafka-console-consumer.sh`/`kcat`.

## Ejemplo ejecutable: `examples/05-kafka`

**Nombres:** groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks`, artifactId `tasks-kafka`, `name=TasksKafka`, carpeta `examples/05-kafka`.

**Código fuente:** se porta `examples/04-cache-redis` completo y verbatim (mismo dominio, auth JWT + caché ya resueltos, no son el foco de esta fase) — mismas ~31 clases, mismas 3 migraciones Flyway copiadas tal cual. Puerto Postgres `5437` (Fase 2 usa `5433`, Fase 3 usa `5434`, Fase 4 usa `5435`, Fase 6 usa `5436`). Kafka usa su puerto por defecto `9092` sin remapear.

**Dependencias nuevas sobre las de Fase 4:** `spring-boot-starter-kafka` (compile), `spring-boot-starter-kafka-test` (test, trae `EmbeddedKafkaBroker`).

### Diseño de eventos

**Dos tipos distintos, para no confundir "señal interna" con "mensaje de Kafka" (aclarado aquí explícitamente porque es la ambigüedad más probable al leer el código una sola vez):**

1. Un evento de aplicación **interno**, solo Spring, nunca serializado ni enviado a ningún sitio — existe únicamente para poder engancharse a `AFTER_COMMIT` (ver más abajo). Nombre: `TaskCompletedEvent`/`TaskCreatedEvent` (o un único `TaskDomainEvent` parametrizado — se decide en el plan), publicado por `TaskServiceImpl` vía `ApplicationEventPublisher.publishEvent(...)` dentro del mismo método transaccional.
2. El **mensaje real de Kafka**: record `TaskEvent(Long taskId, TaskEventType eventType, String titulo, String ownerUsername, Instant timestamp)`, con `TaskEventType` un enum `CREATED`/`COMPLETED`. Este es el único tipo que se serializa a JSON y viaja por el topic `task-events` — lo construye `TaskEventPublisher` (ver flujo abajo) a partir del evento interno, nunca lo construye `TaskServiceImpl` directamente.

`ownerUsername` es **el username del dueño de la tarea** (`task.getUser().getUsername()`), no necesariamente el de quien hizo la petición HTTP — un `ADMIN` puede completar una tarea ajena (`PUT /tasks/{id}` de otro usuario), y la notificación resultante debe llegar a quien es dueño de la tarea, no al admin que la disparó. Esta distinción debe quedar explícita en el código (comentario en el punto donde se construye el `TaskEvent`) y en la página de "Productores y eventos".

- Topic único: `task-events`.
- **Regla de disparo, en un único lugar del código (no repartida):**
  - `CREATED`: siempre que `TaskServiceImpl.create(...)` completa con éxito, sin importar el valor inicial de `completada`.
  - `COMPLETED`: solo cuando `TaskServiceImpl.update(...)` cambia `completada` de `false` a `true` — no en cada actualización de una tarea ya completada, ni al crear una tarea ya marcada como completada (eso sería `CREATED`, no una transición a completada). Justificación explícita a documentar en el código: un evento `COMPLETED` repetido en cada `PUT` de una tarea ya completada sería ruido, no información nueva.
- **Flujo completo, en orden:** `TaskServiceImpl.create()`/`update()` publica el evento de aplicación interno (síncrono, dentro de la transacción) → un `@Component` separado `TaskEventPublisher` (mismo patrón de "bean aparte" que `CachedTaskLookup` en Fase 4) escucha ese evento con `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, construye el `TaskEvent` (con `ownerUsername` tomado de `task.getUser()`, no de `currentUser`) y llama a `kafkaTemplate.send("task-events", ...)` — recién cuando la transacción de base de datos ya confirmó.
- Consumo: `@KafkaListener(topics = "task-events")` en un componente separado `TaskEventListener` (o equivalente), deserializa el `TaskEvent`, resuelve el usuario por `ownerUsername` y crea una fila en `notifications` (`id`, `task_id`, `event_type`, `mensaje`, `user_id`, `created_at`) para ese usuario.
- `GET /notifications`: mismo criterio de ownership que `/tasks` (propias si `USER`, todas si `ADMIN`) — filtra por `notifications.user_id`, que siempre es el dueño de la tarea, nunca el actor que disparó el evento.

### Kafka

- `docker-compose.yml` añade un servicio `kafka` en modo KRaft combinado (broker + controller en el mismo proceso, un solo nodo) — imagen oficial `apache/kafka` en la versión que resuelva el `kafka-clients` real del proyecto generado (verificar al implementar, no fijar de antemano). Puerto `9092` sin remapear.
- Perfil de test: `EmbeddedKafkaBroker` (vía `@EmbeddedKafka`), sin Docker — arranca un broker real embebido en el proceso de test, no un mock del cliente Kafka.
- **Perfil `h2`** (mismo patrón que Fases 2, 3, 4, 6): la app corre sin Docker, pero a diferencia de Postgres (que tiene H2 como alternativa real), **Kafka no tiene un equivalente para correr sin Docker fuera de tests** — el perfil `h2` de esta fase documenta explícitamente que la publicación de eventos queda deshabilitada o falla de forma controlada sin un broker real disponible (decisión exacta de implementación para el plan, no fijada aquí).

### Tests

- Test de productor→consumidor contra `EmbeddedKafkaBroker`: publica un evento real (a través del flujo HTTP completo: crear tarea → verificar que `notifications` tiene la fila esperada), esperando la condición con polling, nunca con `Thread.sleep`.
- Test dedicado de la regla de disparo de `COMPLETED` (transición `false`→`true`, no en cada actualización).

## Fuera de alcance

- Dead-letter queues / reintentos automáticos de mensajes fallidos.
- Múltiples topics por tipo de evento (un único `task-events` para todo).
- Kafka Streams, schema registry/Avro (JSON plano con Jackson, como en Redis en Fase 4).
- Particionamiento/escalado de consumers más allá del comportamiento por defecto de un único consumer.
- Notificaciones leídas/no leídas, push en tiempo real (WebSocket/SSE) — solo tabla + `GET /notifications`.
- Exactamente-una-vez (exactly-once) end-to-end; se acepta at-least-once con el consumidor idempotente solo en el sentido de que un duplicado crea como mucho una notificación duplicada (no un estado inconsistente de datos de negocio).

## Verificación / criterios de aceptación

- Build de Docusaurus sin enlaces rotos.
- `./mvnw test` pasa en verde localmente (incluye el/los test(s) contra `EmbeddedKafkaBroker`, que sí puede ejecutarse sin Docker porque el broker es embebido en el proceso de test).
- `docker-compose.yml` sintácticamente válido (verificación de sintaxis únicamente — Docker no disponible en este entorno, mismo tratamiento que Postgres/Redis en fases anteriores).
- Las 4 páginas de contenido existen, en español, con código consistente con el ejemplo real.
- La página de testing explica por qué un flujo async necesita polling antes de mostrar el código de la solución.
- Sidebar en orden correcto: Productores y eventos → Consumidores → Testing con Kafka → Probar con Kafka real.
- `mvnw` commiteado con bit de ejecución correcto.
- `pom.xml` sin boilerplate vacío de Initializr.
- Un evento `COMPLETED` nunca se dispara dos veces para la misma transición (verificado con el test dedicado de la regla de disparo).
- Ningún evento se publica a Kafka si la transacción de base de datos correspondiente hizo rollback (verificado con un test que fuerza un fallo tras la escritura y confirma que no llegó ningún mensaje al broker embebido).

## Pendientes antes de implementar

1. ~~Aprobación explícita del usuario de este diseño~~ — **hecho, 2026-09-23**.
2. Invocar `writing-plans` para el plan de implementación de la Fase 5.
