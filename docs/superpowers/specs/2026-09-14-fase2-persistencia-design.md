# Fase 2 — Persistencia (diseño)

> Estado: **diseño aprobado por el usuario el 2026-09-14**. Siguiente paso: invocar `writing-plans` para el plan de implementación.

## Contexto

Continuación del roadmap tras la Fase 1 (Fundamentos, completa). La Fase 2 sustituye el repositorio en memoria de la Fase 1 por persistencia real: Spring Data JPA, PostgreSQL, migraciones versionadas con Flyway, y Docker Compose para levantar la base de datos en local.

Decisiones ya cerradas que aplican aquí (ver [[spring-boot-tech-decisions]] y las specs de Fases 0-1): Docusaurus, contenido en español, ejemplos ejecutables autocontenidos en Maven + Java 21, un ejemplo por fase en `examples/`, CI vía GitHub Actions con matriz.

**Lecciones técnicas heredadas de las Fases 0-1 (aplicar desde el diseño del plan, no corregirlas después):**
1. No fijar la versión de Spring Boot al generar el ejemplo — omitir `bootVersion` en la llamada a Initializr.
2. Verificar explícitamente el bit de ejecución de `mvnw` antes de cada commit (`git update-index --chmod=+x` + `git ls-files -s` mostrando `100755`).
3. Limpiar el `pom.xml` generado por Initializr (quitar `<description/>`, `<url/>`, `<licenses>`, `<developers>`, `<scm>` vacíos) — incluirlo como paso explícito del plan esta vez, no como corrección posterior.
4. Spring Boot 4.x cambió paquetes conocidos respecto a la 3.x (`@AutoConfigureMockMvc`, nombres de starters, Jackson bajo `tools.jackson`) — verificar la realidad del proyecto generado, no asumir que sigue igual que en la Fase 1.
5. Si se reutiliza nombrado de la Fase 1, no hay renombrado de clases existentes que afecte a la documentación de la Fase 1 — `examples/02-persistencia` es un proyecto Maven completamente independiente y nuevo; no se modifica `examples/01-fundamentos`.

## Continuidad pedagógica

**Mismo dominio que la Fase 1:** gestor de tareas (`Task`: titulo, descripcion, completada). Mismos endpoints, misma validación, mismo manejo de errores. El contraste directo entre "Fase 1: repositorio en memoria" y "Fase 2: el mismo repositorio, pero con Spring Data JPA" es el eje pedagógico de esta fase — demuestra en código real la promesa hecha en la página de DI de la Fase 1 ("puedes cambiar la implementación sin tocar el service").

`examples/02-persistencia` es un proyecto Maven **independiente y autocontenido**, no una modificación de `examples/01-fundamentos`.

## Estructura de contenido

`docs-site/docs/02-persistencia/` (el `_category_.json` con `position: 4` ya existe de la Fase 0, no se toca):

- `index.md` — deja de ser el placeholder "en construcción"; introduce la fase con el contraste explícito Fase 1 (memoria) → Fase 2 (Postgres real), y enlaza a las 4 sub-páginas.
- 4 páginas, una por subtema:
  1. **Docker Compose y Postgres** — levantar una base de datos real en local con un comando.
  2. **Migraciones con Flyway** — versionar el esquema de la base de datos como código.
  3. **Spring Data JPA** — entidades, repositorios, y cuánto código desaparece frente al repositorio en memoria de la Fase 1.
  4. **Probar la app con datos reales** — conectar todo y ver la API funcionando contra Postgres.

## Ejemplo ejecutable: `examples/02-persistencia`

**Nombres:** groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks` (mismo paquete que la Fase 1 — refuerza la narrativa "es el mismo código, evolucionado"; no hay colisión posible porque cada ejemplo es un proyecto Maven independiente, nunca en el mismo classpath), artifactId `tasks-jpa`, `name=TasksJpa`, carpeta `examples/02-persistencia`.

**Capas:** `TaskController`, `TaskService`/`TaskServiceImpl`, `TaskRequest`, `TaskNotFoundException`, `ApiError`/`ValidationApiError`/`GlobalExceptionHandler` se reintroducen casi sin cambios respecto a la Fase 1 (cada ejemplo es autocontenido). No son el foco de esta fase — se presentan como ya conocidos, sin volver a explicarlos en detalle en el contenido.

Dos cambios reales, que sí son el foco:
- **`Task` pasa de `record` a `@Entity` JPA** — con `@Id`/`@GeneratedValue`, constructor sin argumentos protegido (requisito de JPA) y campos mutables (getters/setters). Es un punto didáctico explícito: por qué una entidad JPA no puede ser un record inmutable.
- **`TaskRepository` deja de tener implementación escrita a mano** — pasa a ser `interface TaskRepository extends JpaRepository<Task, Long> {}`, sin cuerpo. Spring Data genera la implementación en tiempo de ejecución. `InMemoryTaskRepository` desaparece (no existía en este proyecto nuevo, así que no hay nada que borrar de la Fase 1).
- Los métodos de escritura de `TaskServiceImpl` (`create`, `update`, `delete`) se anotan con `@Transactional` — primera aparición de esta anotación en el roadmap, justificada aquí porque ahora hay un recurso transaccional real (la base de datos) que gestionar correctamente. `update` pasa a mutar la entidad gestionada y guardarla, en vez de reconstruir un objeto inmutable como en la Fase 1.

**Base de datos:**
- `docker-compose.yml` en la raíz del ejemplo: un único servicio Postgres (imagen `postgres:16`), puerto `5433` en el host (para no chocar con una instalación local de Postgres en el `5432` por defecto) mapeado al `5432` del contenedor, volumen nombrado para persistir datos entre reinicios.
- Perfil por defecto (`application.yml`): datasource apuntando a `jdbc:postgresql://localhost:5433/tasks`, Flyway activado, **`spring.jpa.hibernate.ddl-auto: validate`** explícito. Sin fijar esto, Hibernate podría intentar generar/alterar el esquema por su cuenta (especialmente en bases embebidas como H2, donde el valor por defecto de Spring Boot es `create-drop` incluso con Flyway presente) y entrar en conflicto con las migraciones — `validate` deja el esquema exclusivamente en manos de Flyway y solo comprueba que las entidades JPA coincidan con él, fallando rápido y con un mensaje claro si no coinciden. Mismo valor en el perfil `test`.
- Migración Flyway `V1__create_tasks_table.sql`: crea la tabla `tasks` (id, titulo, descripcion, completada) con SQL compatible tanto con Postgres como con H2 en modo compatibilidad Postgres (para que la misma migración se pueda ejecutar en ambos motores sin duplicar SQL).

**Tests:** perfil `test` (`application-test.yml`) con **H2 en memoria** en modo compatibilidad PostgreSQL — no Postgres real, no Testcontainers (eso es la Fase 6, explícitamente fuera de alcance aquí). Flyway corre igual contra H2, así que las migraciones reales quedan probadas, no solo el mapeo JPA. Mismos 9 casos MockMvc que la Fase 1 (crear válida/inválida, listar, obtener existente/no existente, actualizar existente/no existente, eliminar existente/no existente), con `@ActiveProfiles("test")`, sin necesitar Docker para `mvn test` ni en CI.

**Infraestructura:** mismo patrón que Fases 0-1 — proyecto generado vía Spring Initializr (sin fijar `bootVersion`), Java 21, Maven Wrapper con bit de ejecución verificado explícitamente antes del commit, `pom.xml` limpiado de boilerplate vacío de Initializr como paso explícito del plan, `README.md` con instrucciones (incluyendo cómo levantar Postgres con `docker compose up`). Se añade `02-persistencia` a la matriz de `examples-ci.yml` — el job de CI **no** necesita un servicio Postgres porque los tests usan H2.

**Fuera de alcance de esta fase** (para no invadir fases futuras): sin Testcontainers (Fase 6), sin autenticación (Fase 3), sin migraciones de datos complejas más allá de la tabla inicial, sin pool de conexiones con tuning explícito (HikariCP por defecto de Spring Boot).

## Verificación / criterios de aceptación

- Build de Docusaurus sin enlaces rotos.
- `mvn verify` pasa en `examples/02-persistencia` usando H2 (sin necesitar Docker), local y en CI.
- `docker compose up` levanta Postgres correctamente y la app conecta contra él al ejecutarse con `./mvnw spring-boot:run` — **Docker no está disponible en el entorno donde se ejecutan los subagentes de este proyecto** (confirmado: `docker --version` → comando no encontrado), igual que no hubo navegador disponible en las Fases 0-1. Esta verificación queda genuinamente pendiente para un humano con Docker instalado; el plan debe declararlo explícitamente como un pendiente abierto (no simularlo ni darlo por bueno) y centrar la verificación automatizable en que `docker-compose.yml` sea sintácticamente válido y en que la suite de tests con H2 (que sí prueba las migraciones Flyway reales) pase limpia.
- Las 4 páginas de contenido existen, en español, con código consistente con el ejemplo real.
- Revisión del sidebar: la Fase 2 muestra sus 4 sub-páginas en el menú, en el orden correcto (Docker Compose y Postgres → Flyway → Spring Data JPA → Probar con datos reales).
- `mvnw` commiteado con bit de ejecución correcto (verificado explícitamente, no dado por hecho).
- `pom.xml` sin boilerplate vacío de Initializr.

## Pendientes antes de implementar

1. ~~Aprobación explícita del usuario de este diseño~~ — **hecho, 2026-09-14**.
2. Invocar `writing-plans` para el plan de implementación de la Fase 2.
