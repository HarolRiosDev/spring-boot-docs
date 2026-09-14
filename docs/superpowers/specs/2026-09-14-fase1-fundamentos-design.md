# Fase 1 — Fundamentos (diseño)

> Estado: **diseño aprobado por el usuario el 2026-09-14**. Siguiente paso: invocar `writing-plans` para el plan de implementación.

## Contexto

Continuación del roadmap tras la Fase 0 (fundación del repo, completa). La Fase 1 es la primera fase de contenido real de Spring Boot: cubre los fundamentos que cualquier lector nuevo en el framework necesita antes de tocar persistencia, seguridad, caché, etc.

Decisiones ya cerradas que aplican aquí (ver [[spring-boot-tech-decisions]] / `docs/superpowers/specs/2026-09-14-fase0-fundacion-design.md`): Docusaurus, contenido en español, ejemplos ejecutables autocontenidos en Maven + Java 21, un ejemplo por fase en `examples/`, CI vía GitHub Actions con matriz (ya preparada en `examples-ci.yml` para añadir una línea por fase).

**Dato importante heredado de la Fase 0:** al generar el ejemplo, comprobar en `start.spring.io` la versión estable actual de Spring Boot — no asumir que sigue siendo `4.1.1` (la Fase 0 tuvo que sustituir la versión planeada originalmente porque `start.spring.io` había dejado de servir la línea 3.x). Si la versión cambia, aplicar el mismo patrón que la Fase 0: comprobar si algo del `pom.xml` o los imports de test cambia de paquete, verificar contra Maven Central / Javadoc oficial antes de asumir nada.

## Alcance de la Fase 1

Temas del roadmap para esta fase (ya reflejados en el placeholder actual de `docs-site/docs/01-fundamentos/index.md`): inyección de dependencias, beans, controladores REST, capas (controller/service/repository), validación, manejo de errores.

## Estructura de contenido

`docs-site/docs/01-fundamentos/` (el `_category_.json` con `position: 3` ya existe de la Fase 0, no se toca):

- `index.md` — deja de ser el placeholder "🚧 en construcción"; pasa a ser una página de introducción a la fase con la lista de las 5 sub-páginas de abajo y un enlace al ejemplo ejecutable.
- 5 páginas, una por subtema (DI y beans se combinan en una sola página porque son, en la práctica, el mismo concepto enseñado junto):
  1. **Inyección de dependencias y beans** — qué es un bean, `@Component`/`@Service`/`@Repository`, inyección por constructor (forma recomendada, con motivo).
  2. **Controladores REST** — `@RestController`, `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`, cuerpo de petición/respuesta.
  3. **Capas (controller → service → repository)** — por qué separar responsabilidades, cómo se comunican las capas entre sí.
  4. **Validación** — Bean Validation (`@NotBlank`, `@Valid`), respuestas 400.
  5. **Manejo de errores** — `@RestControllerAdvice`, excepciones propias, respuestas de error estructuradas.

Cada página usa código extraído/basado en el ejemplo real (`examples/01-fundamentos`) para que documentación y código no diverjan, y termina con un enlace a la carpeta del ejemplo en el repo.

Se actualiza `sidebars.js`/autogeneración: no requiere cambios (autogenerado por posición ya configurado), solo el `sidebar_position` dentro de cada uno de los 5 archivos nuevos para fijar su orden interno dentro de la categoría.

## Ejemplo ejecutable: `examples/01-fundamentos`

**Dominio:** gestor de tareas (Task: `id`, `titulo`, `descripcion`, `completada`) — CRUD completo, para poder ilustrar los 5 conceptos de la sección de contenido.

**Capas:**
- `TaskController` (REST) → `TaskService` (interfaz + implementación — para que la inyección de dependencias por constructor tenga un motivo real de existir, no solo una clase concreta inyectándose a sí misma) → `TaskRepository` (interfaz + implementación en memoria; sin base de datos real todavía — eso es la Fase 2/Persistencia).

**Endpoints:**
- `POST /tasks` — crear (201, cuerpo con la tarea creada)
- `GET /tasks` — listar todas
- `GET /tasks/{id}` — obtener una (200 / 404)
- `PUT /tasks/{id}` — actualizar (200 / 404)
- `DELETE /tasks/{id}` — eliminar (204 / 404)

**Validación:** `@NotBlank` en `titulo` vía Bean Validation (`jakarta.validation`), `@Valid` en los métodos del controlador que reciben cuerpo, respuesta 400 con el detalle de los campos inválidos.

**Manejo de errores:** `@RestControllerAdvice` global — `TaskNotFoundException` (propia, lanzada por el service) → 404; `MethodArgumentNotValidException` (fallo de `@Valid`) → 400; ambos casos devuelven un cuerpo JSON estructurado y consistente (ej. `status`, `message`, `timestamp`, y para validación una lista de errores por campo).

**Tests:** mismo patrón TDD que `00-hello-world` (Fase 0) — tests de integración con `MockMvc`, escritos primero (RED) y luego implementados (GREEN). Cobertura mínima: crear tarea válida (201), crear tarea inválida (400), listar, obtener por id existente (200) y no existente (404), actualizar, eliminar.

**Nombres:** siguiendo el patrón de Fase 0 (`dev.springbootdocs.examples.hello`, artifactId `hello-world`, carpeta `00-hello-world`) — groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks`, artifactId `tasks`, carpeta `examples/01-fundamentos`. Campos de `Task` en el código: `id`, `titulo`, `descripcion`, `completada` (sin acentos en los identificadores Java, como es estándar).

**Infraestructura:** mismo patrón que Fase 0 — proyecto generado vía Spring Initializr (API `start.spring.io`, comprobando la versión estable actual), Java 21, Maven Wrapper (`mvnw`/`mvnw.cmd`, con el bit de ejecución correcto desde el primer commit — la Fase 0 tuvo un bug real por olvidar esto), `README.md` con instrucciones de ejecución y test. Se añade `01-fundamentos` como nueva entrada de `matrix.example` en `.github/workflows/examples-ci.yml` (diseño ya preparado para esto en la Fase 0, solo hace falta añadir la línea).

**Fuera de alcance de esta fase** (para no invadir fases futuras): sin base de datos real (Fase 2), sin autenticación/autorización (Fase 3), sin tests unitarios de service con mocks más allá del MockMvc de integración (Fase 6 es la fase de testing propiamente dicha).

## Verificación / criterios de aceptación

- Build de Docusaurus sin enlaces rotos (igual que Fase 0, `onBrokenLinks: 'throw'`).
- `mvn verify` pasa en `examples/01-fundamentos`, local y en CI (matriz actualizada).
- Las 5 páginas de contenido existen, en español, con código consistente con el ejemplo real.
- Revisión visual del sidebar: la fase 1 ahora muestra sus 5 sub-páginas en el menú, en el orden correcto.

## Pendientes antes de implementar

1. ~~Aprobación explícita del usuario de este diseño~~ — **hecho, 2026-09-14**.
2. Invocar `writing-plans` para el plan de implementación de la Fase 1.
