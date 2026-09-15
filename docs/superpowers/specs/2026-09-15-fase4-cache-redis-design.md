# Fase 4 — Caché y Redis (diseño)

> Estado: **diseño aprobado por el usuario el 2026-09-15**. Siguiente paso: invocar `writing-plans` para el plan de implementación.

## Contexto

Continuación del roadmap tras la Fase 3 (Security/JWT, completa). La Fase 4 añade caché al API de tareas: Spring Cache (abstracción) con Redis como backend real, y una entrada en memoria (`ConcurrentMapCacheManager`) para tests/desarrollo sin Docker.

Decisiones ya cerradas que aplican aquí (ver [[spring-boot-tech-decisions]] y las specs de Fases 0-3): Docusaurus, contenido en español, ejemplos ejecutables autocontenidos en Maven + Java 21, un ejemplo por fase en `examples/`, CI vía GitHub Actions con matriz.

**Lecciones técnicas heredadas de las Fases 0-3 (aplicar desde el diseño del plan, no corregirlas después):**
1. No fijar la versión de Spring Boot al generar el ejemplo — omitir `bootVersion` en la llamada a Initializr.
2. Verificar explícitamente el bit de ejecución de `mvnw` antes de cada commit.
3. Limpiar el `pom.xml` generado por Initializr como paso explícito del plan.
4. Spring Boot 4.x / Spring Framework 7 cambió paquetes conocidos respecto a la 3.x (`@AutoConfigureMockMvc`, starters `-webmvc`, Jackson bajo `tools.jackson`) — verificar la realidad del proyecto generado, no asumir. **Nuevo riesgo específico de esta fase:** verificar si `GenericJackson2JsonRedisSerializer` (Spring Data Redis) funciona sin fricción con Jackson 3, o si requiere algún ajuste — no asumir que el ecosistema Redis ya migró.
5. `examples/04-cache-redis` es un proyecto Maven **completamente independiente y nuevo**; no se modifica `examples/03-security-jwt`.
6. **Una revisión final de todo el branch (no solo por-task) es indispensable** cuando varias tasks tocan el mismo componente compartido — mantener el paso de revisión final con el modelo más capaz disponible, incluso si cada task salió "Approved" individualmente (encontró 3 problemas reales en Fase 3 que ninguna revisión por-task vio).
7. Cuidado con dependencias lazy de JPA leídas fuera de una transacción explícita — mapear a DTO siempre dentro del `@Transactional` del service, nunca en el controller (ya corregido en Fase 3, mantener el patrón).
8. Un handler de excepción global y genérico no debe llevar un mensaje específico de un solo caller.
9. **Nuevo de Fase 4 (patrón ya usado en Fases 2-3, formalizado aquí):** todo ejemplo que dependa de un servicio externo (Postgres, Redis) debe tener un perfil `h2`/en-memoria documentado en su README para poder ejecutarse sin Docker — no es opcional, es parte del entregable de cada fase desde ahora.

## Continuidad pedagógica

**Mismo dominio que las Fases 1-3:** gestor de tareas, ahora con caché. Se reintroducen `User`/`Role`/`AuthController`/`JwtService`/`JwtAuthenticationFilter`/`SecurityConfig`/`TaskController`/`AdminController`/etc. de la Fase 3 casi sin cambios (no son el foco), y se añade caché sobre las lecturas de tareas. El eje pedagógico es "cachear no exige tocar la lógica de negocio, solo capas finas alrededor" — y, en sentido contrario, "cachear sin cuidado puede filtrar datos entre usuarios si no se piensa la clave e invalidación con cabeza".

`examples/04-cache-redis` es un proyecto Maven **independiente y autocontenido**, no una modificación de `examples/03-security-jwt`.

## Estructura de contenido

`docs-site/docs/04-cache-redis/` (el `_category_.json` con `position: 6` ya existe de la Fase 0, no se toca):

- `index.md` — deja de ser el placeholder "en construcción"; introduce la fase con el contraste "cada `GET /tasks/{id}` repetido volvía a la base de datos, ahora la primera lectura cachea, las siguientes no", y enlaza a las 4 sub-páginas.
- 4 páginas, una por subtema:
  1. **Spring Cache básico** — `@EnableCaching`, `@Cacheable`, la abstracción de caché independiente del backend, y la trampa de la auto-invocación (llamar a un método `@Cacheable` desde otro método de la misma clase no pasa por el proxy de Spring, así que la caché no se aplica — sin ningún error visible).
  2. **Redis como backend** — Redis en `docker-compose.yml`, `RedisCacheManager`, TTL explícito, por qué serializar a JSON en vez de con serialización Java nativa.
  3. **Invalidación de caché** — `@CacheEvict` en escrituras, el problema de la caché desactualizada, y por qué la comprobación de ownership se ejecuta siempre — nunca se salta por un acierto de caché.
  4. **Probar con caché real** — perfil sin Docker (`ConcurrentMapCacheManager`), cómo confirmar en un test que la caché funciona de verdad, y verificación manual con `redis-cli` contra Redis real.

## Ejemplo ejecutable: `examples/04-cache-redis`

**Nombres:** groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks`, artifactId `tasks-cache`, `name=TasksCache`, carpeta `examples/04-cache-redis`.

**Dependencias nuevas sobre las de Fase 3:** `cache` (Spring Initializr, `spring-boot-starter-cache`) + `spring-boot-starter-data-redis`.

### Diseño de caché (decisión clave, cerrada tras dos rondas de refinamiento)

**No** se mete al usuario en la clave de caché. En su lugar, se separa "traer el dato" (cacheable) de "autorizar el acceso" (siempre se ejecuta):

- Componente nuevo `CachedTaskLookup` (`@Component`), con un único método `findById(Long id)` anotado `@Cacheable(value = "tasks", key = "#id")` que delega en `TaskRepository.findById(id)` y lanza `TaskNotFoundException` si no existe (una excepción no se cachea — solo se cachea el `Task` cuando existe). Sin lógica de negocio ni de seguridad — solo la lectura.
- `TaskServiceImpl.findById(id, currentUser)` llama a `cachedTaskLookup.findById(id)` (venga de caché o de la BD, indistinguible desde aquí) y **siempre** ejecuta `requireAccess(task, currentUser)` después — una entrada de caché nunca se sirve sin comprobar el permiso de quien pregunta, en cada llamada.
- `TaskServiceImpl.update`/`delete` se anotan `@CacheEvict(value = "tasks", key = "#id")` para invalidar la misma entrada al escribir. `create` no necesita evict (no hay nada cacheado todavía para un id nuevo).

**Por qué en un componente aparte y no un método privado/interno de `TaskServiceImpl` que se auto-invoque:** las anotaciones de caché de Spring funcionan vía proxy AOP; una llamada `this.metodo()` dentro de la misma clase no pasa por el proxy, así que `@Cacheable` no se aplicaría — sin ningún error, simplemente sin cachear nunca. `CachedTaskLookup` es un bean distinto, así que la llamada desde `TaskServiceImpl` sí atraviesa el proxy. Esto es exactamente lo que enseña la página "Spring Cache básico".

**Detalle de corrección ya verificado (no requiere configuración extra):** `@CacheEvict` solo se dispara por defecto si el método anotado termina **sin lanzar excepción** (`beforeInvocation = false`, el valor por defecto). Como `update`/`delete` llaman primero a la comprobación de ownership (que lanza `TaskNotFoundException`/`TaskAccessDeniedException` antes de tocar la base de datos si el acceso no es válido), un intento fallido de escritura nunca dispara la invalidación — coherente, porque no hubo ninguna escritura real que invalidar. No hace falta fijar `beforeInvocation` explícitamente; el comportamiento por defecto ya es el correcto aquí. Vale la pena explicarlo en "Invalidación de caché" como ejemplo de por qué el orden de las comprobaciones importa.

**Sin migraciones Flyway nuevas:** `examples/04-cache-redis` reutiliza las mismas tres migraciones de la Fase 3 (`V1__create_users_table.sql`, `V2__create_tasks_table.sql`, `V3__seed_admin_user.sql`), copiadas tal cual a este proyecto nuevo — no hay cambio de esquema, la caché no toca la capa de persistencia. Mismo criterio de independencia que las fases anteriores: proyecto Maven separado, sin compartir archivos con `examples/03-security-jwt`.

**`findAll` no se cachea** en esta fase (fuera de alcance) — cachear una lista que cambia con cualquier `create`/`delete` de cualquier usuario añade una complejidad de invalidación (¿qué claves invalidar? ¿por usuario, por rol?) que no aporta al objetivo didáctico de esta fase.

### Redis

- `docker-compose.yml` del ejemplo añade **dos servicios**: `postgres` (reintroducido de Fase 3, puerto `5435` en el host — Fase 2 usa `5433`, Fase 3 usa `5434`) y `redis:7-alpine` (puerto por defecto `6379`, sin necesidad de remapear salvo que el usuario ya tenga un Redis local corriendo).
- `RedisCacheManager` configurado (vía `RedisCacheConfiguration`/`RedisCacheManagerBuilderCustomizer` — la API exacta se confirma en el plan, no se fija aquí) con:
  - **TTL explícito** (10 minutos) — sin TTL, las entradas viven para siempre y una fase de caché sin expiración es una lección incompleta.
  - **Serialización JSON** (`GenericJackson2JsonRedisSerializer`) para los valores, en vez de la serialización Java (`JdkSerializationRedisSerializer`, el default de Spring si no se configura nada) — más realista, y evita depender de que `Task`/`TaskResponse` implementen `Serializable`.

### Tests y perfil sin Docker

- Perfil de test (`src/test/resources/application.yml`, igual que Fases 2-3): añade `spring.cache.type: simple` (activa el `ConcurrentMapCacheManager` autoconfigurado por Spring Boot) junto al H2 ya establecido — cero Docker, cero Redis, misma abstracción `@Cacheable`/`@CacheEvict` funcionando de verdad contra una caché real en memoria (no un mock).
- **Verificación de que la caché realmente funciona:** un test dedicado inyecta el `CacheManager` de Spring y comprueba directamente `cacheManager.getCache("tasks").get(id)` — no nulo tras una lectura, nulo tras un `update`/`delete`. Se prueba el comportamiento real de la abstracción de caché, no un mock de ella.
- **Perfil `h2`** (mismo patrón ya establecido en Fases 2-3, ahora formalizado como parte estándar de cada fase con dependencia externa): `application-h2.yml` con H2 en archivo + `spring.cache.type: simple`, para correr la app real (`SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run`) sin Docker ni Redis ni Postgres.

### Infraestructura

Mismo patrón que Fases 0-3: proyecto generado vía Spring Initializr (sin fijar `bootVersion`), Java 21, Maven Wrapper con bit de ejecución verificado, `pom.xml` limpiado de boilerplate de Initializr, `README.md` con instrucciones (incluyendo la sección "Sin Docker"). Se añade `04-cache-redis` a la matriz de `examples-ci.yml` — el job de CI no necesita Postgres ni Redis porque los tests usan H2 + `ConcurrentMapCacheManager`.

**Docker sigue sin estar disponible en el entorno donde se implementa esta fase** — verificación limitada a sintaxis de `docker-compose.yml`, verificación real contra Postgres/Redis reales queda como pendiente explícito para el usuario (mismo tratamiento que Fases 2-3).

## Fuera de alcance

- Cachear `findAll`/listados de tareas.
- Invalidación de caché distribuida entre múltiples instancias de la app, Redis Cluster/Sentinel, Redis Pub/Sub.
- Métricas de caché vía Actuator (candidato natural para Fase 7 — Observabilidad).
- Patrones write-through/refresh-ahead, protección contra cache stampede.
- Redis como cola de mensajes o almacén de sesiones (fuera del tema "caché" de esta fase).

## Verificación / criterios de aceptación

- Build de Docusaurus sin enlaces rotos.
- `mvn verify` pasa en `examples/04-cache-redis` usando H2 + `ConcurrentMapCacheManager` (sin necesitar Docker ni Redis), local y en CI.
- Test dedicado confirma, contra el `CacheManager` real, que una lectura cachea y un `update`/`delete` invalida esa entrada.
- Las 4 páginas de contenido existen, en español, con código consistente con el ejemplo real.
- Sidebar en orden correcto: Spring Cache básico → Redis como backend → Invalidación de caché → Probar con caché real.
- `mvnw` commiteado con bit de ejecución correcto.
- `pom.xml` sin boilerplate vacío de Initializr.
- `docker-compose.yml` sintácticamente válido (dos servicios); verificación real contra Postgres/Redis documentada como pendiente explícito.
- Perfil `h2` documentado y verificado manualmente (igual que Fases 2-3): la app corre de punta a punta sin Docker.
- Ningún endpoint de `/tasks` sirve una tarea a un usuario sin autorización, ni siquiera en un acierto de caché (verificado con un test que fuerza un acierto de caché y luego intenta acceder con un usuario distinto sin permiso).

## Pendientes antes de implementar

1. ~~Aprobación explícita del usuario de este diseño~~ — **hecho, 2026-09-15**.
2. Invocar `writing-plans` para el plan de implementación de la Fase 4.
