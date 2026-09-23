# Fase 6 — Testing (diseño)

> Estado: **diseño aprobado por el usuario el 2026-09-23**. Siguiente paso: invocar `writing-plans` para el plan de implementación.

## Contexto

Continuación del roadmap tras la Fase 4 (Caché y Redis, completa). Por decisión explícita del usuario (2026-09-22), el **orden de implementación** salta la Fase 5 (Kafka) y construye primero la Fase 6 (Testing) — "muchos proyectos funcionan sin mensajería pero la parte de test es necesaria sí o sí". El roadmap público del sitio no se renumera (`06-testing` sigue en `position: 8` en su `_category_.json`, ya existente desde la Fase 0); solo cambia el orden real de construcción.

Decisiones ya cerradas que aplican aquí (ver [[spring-boot-tech-decisions]]): Docusaurus, contenido en español, ejemplos ejecutables autocontenidos en Maven + Java 21, un ejemplo por fase en `examples/`, CI vía GitHub Actions con matriz (`.github/workflows/examples-ci.yml`, ya ejecuta `./mvnw -B verify`, que cubre tanto Surefire como Failsafe). Solo hace falta añadir `06-testing` a la lista `matrix.example` — el workflow en sí no necesita ningún otro cambio.

**Lecciones técnicas heredadas (Fases 0-4) que aplican directamente a esta fase:**
1. No fijar la versión de Spring Boot al generar el ejemplo.
2. Verificar el bit de ejecución de `mvnw` antes de cada commit.
3. Limpiar el `pom.xml` generado por Initializr.
4. Verificar paquetes reales de Spring Boot 4.x/Spring Framework 7 contra el proyecto generado, no asumir.
5. `examples/06-testing` es un proyecto Maven **completamente independiente**, no una modificación de `examples/04-cache-redis`.
6. **Revisión final de todo el branch obligatoria** — en Fases 3 y 4 atrapó problemas reales invisibles por-task.
7. Mapeo a DTO siempre dentro del `@Transactional` del service, nunca en el controller (ya así en el código portado).
8. **Divergencia test-vs-producción oculta bugs** — el tema central y literal de esta fase. La lección de Fase 4 (compilar/ejecutar el cliente real contra clases reales cuando no hay Docker) **no aplica igual aquí**: Testcontainers necesita Docker en tiempo de ejecución, no solo los jars — no hay forma de probar el contenedor sin Docker real. La mitigación aquí es distinta: revisión de código línea a línea contra la documentación de Testcontainers/`@ServiceConnection` (no se puede verificar empíricamente en este entorno), y tratar el primer `mvn verify` en verde en CI (con Docker real) como un gate explícito antes de dar la fase por cerrada — no basta con que compile localmente.
9. Cuidado con relaciones lazy de JPA al serializar/cachear — ya resuelto en el código portado de Fase 4, no reabrir.

## Continuidad pedagógica

**Mismo dominio que las Fases 1-4:** gestor de tareas con auth JWT y caché, portado verbatim. El foco de esta fase no es el dominio sino **la propia disciplina de testing** — hasta ahora (Fases 1-4) todo test ha sido de integración (`@SpringBootTest`/MockMvc, contexto Spring real, base de datos real o H2). Esta fase introduce, por primera vez, tests unitarios puros (Mockito, sin contexto) y Testcontainers, usando el código ya familiar como sustrato para no distraer con dominio nuevo.

**Énfasis pedagógico explícito pedido por el usuario:** muchos desarrolladores junior no saben cuándo ni cómo usar Mockito — no es solo un problema de sintaxis. La página de Mockito (página 2) debe **liderar con el criterio de decisión y los errores comunes**, no con la mecánica de `@Mock`/`when()`/`verify()` como si fuera lo primero que importa. Contenido explícito a cubrir ahí:
- Criterio: ¿la clase tiene lógica de negocio no trivial y dependencias fáciles de mockear (interfaces, sin `final`)? → unitario con Mockito. ¿Lo que hay que probar es que Spring conecta todo bien, seguridad, serialización, una query real? → integración (lo ya conocido).
- Errores comunes de junior: mockear todo indiscriminadamente (incluidos objetos de valor simples que no hace falta mockear), sobre-usar `verify()` y terminar probando detalles de implementación en vez de comportamiento observable, confundir `@Mock`/`Mockito.mock()` (unitario puro) con `@MockBean`/`@MockitoBean` (integración con contexto Spring parcialmente mockeado — herramientas para problemas distintos), no resetear stubs entre tests cuando hace falta.
- La página 4 (síntesis) cierra el círculo con una tabla de decisión que cubre los tres estilos (unitario / integración / Testcontainers) ahora que las tres opciones ya se explicaron con código real.

`examples/06-testing` es un proyecto Maven **independiente y autocontenido**, no una modificación de `examples/04-cache-redis`.

## Estructura de contenido

`docs-site/docs/06-testing/` (el `_category_.json` con `position: 8` ya existe de la Fase 0, no se toca):

- `index.md` — deja de ser el placeholder "en construcción"; introduce la fase con el contraste "hasta ahora todo test pasaba por un contexto Spring completo; aquí aprendes cuándo eso es demasiado (o demasiado poco)", y enlaza a las 4 sub-páginas.
- 4 páginas, una por subtema:
  1. **JUnit avanzado** — `@ParameterizedTest` (`@CsvSource`, `@NullAndEmptySource`), `@Nested` para agrupar casos, ciclo de vida (`@BeforeEach` vs `@BeforeAll`). Ejemplo concreto: `TaskControllerTest` hoy solo tiene **un** test de validación (`createTask_withTituloOverMaxLength_returnsBadRequest`, título > 255 caracteres); `TaskRequest` valida además `titulo` en blanco (`@NotBlank`) y `descripcion` > 1000 caracteres, sin test hoy. Se reemplaza el test único por un `@ParameterizedTest` que cubre los tres casos (y cualquier combinación adicional que se quiera añadir sin duplicar código), demostrando por qué parametrizar vale la pena precisamente cuando la cobertura crece.
  2. **Mockito y tests unitarios** — primera vez en todo el sitio que se prueba una clase sin contexto Spring. Lidera con el criterio de "cuándo sí / cuándo no" y los errores comunes de junior (ver sección anterior), y solo después muestra el test real: `TaskServiceImplTest` con `TaskRepository` y `CachedTaskLookup` mockeados (`Mockito.mock()`/`@ExtendWith(MockitoExtension.class)`), cubriendo la lógica de ownership (403 en tarea ajena vs 404 en tarea inexistente) — lógica que hasta ahora solo se había probado indirectamente vía HTTP con contexto completo.
  3. **Testcontainers** — `@Testcontainers` + `@Container` + `PostgreSQLContainer` + contenedor de Redis (módulo `com.redis:testcontainers-redis`), ambos con `@ServiceConnection` (integración nativa de Spring Boot, sin cablear propiedades a mano). Tests `*IT.java`, ejecutados por `maven-failsafe-plugin` en la fase `verify` (`./mvnw verify`, ya el comando usado en todo el proyecto). Explica por qué esto por fin verifica contra infraestructura real lo que Fases 2-4 solo pudieron probar con H2/`ConcurrentMapCacheManager`.
  4. **Página de síntesis** — tabla de decisión: test unitario (Mockito) vs. integración (contexto Spring + H2, lo ya conocido) vs. Testcontainers (infraestructura real en contenedor). Cuándo usar cada uno, con ejemplos del propio proyecto.

## Ejemplo ejecutable: `examples/06-testing`

**Nombres:** groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks`, artifactId `tasks-testing`, `name=TasksTesting`, carpeta `examples/06-testing`.

**Código fuente:** se porta `examples/04-cache-redis` completo y verbatim (mismo dominio, auth JWT + caché ya resueltos, no son el foco de esta fase) — mismas clases (`Task`, `User`, `TaskServiceImpl`, `CachedTaskLookup`, `SecurityConfig`, `JwtService`, etc.), mismas 3 migraciones Flyway copiadas tal cual. `application.yml`/`application-h2.yml` ajustados a puerto Postgres `5436` (Fase 2 usa `5433`, Fase 3 usa `5434`, Fase 4 usa `5435`).

**Dependencias nuevas sobre las de Fase 4:**
- `org.springframework.boot:spring-boot-testcontainers` (integración `@ServiceConnection`, dependencia de test).
- `org.testcontainers:junit-jupiter` + `org.testcontainers:postgresql` (via el BOM de Testcontainers que gestiona Spring Boot Parent).
- `com.redis:testcontainers-redis` (módulo oficial con soporte `@ServiceConnection` nativo — evita cablear un `GenericContainer` a mano).

**Estructura de tests nueva:**
- `TaskControllerTest.java` (existente, modificado): el único test de validación actual se reemplaza por un `@ParameterizedTest` + `@Nested` que cubre título en blanco, título > 255 caracteres y descripción > 1000 caracteres.
- `src/test/java/.../TaskServiceImplTest.java` (nuevo): Mockito puro, `TaskRepository`/`CachedTaskLookup` mockeados, sin `@SpringBootTest`, sin base de datos.
- `src/test/java/.../TaskApiIT.java` (o similar, sufijo `IT` obligatorio para que Failsafe lo recoja y Surefire lo excluya por convención — patrón estándar de Maven, sin configuración extra de exclusión necesaria): `@Testcontainers`, Postgres + Redis reales, ejercitando el flujo completo (auth → crear tarea → leer con caché → invalidar) contra infraestructura real.
- `pom.xml` gana el plugin `maven-failsafe-plugin` (no estaba en Fases 1-4, que solo usaban Surefire) enlazado a las fases `integration-test`/`verify`.

**Perfil `h2` (mismo patrón que Fases 2-4):** se mantiene `application-h2.yml` para correr la app real sin Docker — los tests `*IT.java` con Testcontainers son un mecanismo aparte, no reemplazan este perfil manual.

### Docker no disponible en este entorno (tratamiento explícito)

Ningún entorno usado hasta ahora para implementar el proyecto tiene Docker instalado. Consecuencia concreta para el plan de implementación:
- `./mvnw test` (solo Surefire: JUnit avanzado + Mockito) debe pasar en verde localmente — no depende de Docker.
- `./mvnw verify` (incluye Failsafe → los `*IT.java` con Testcontainers) **fallará localmente** por falta de Docker — esto es esperado, no un bug a arreglar durante la implementación. Se usa `./mvnw verify -DskipITs` (flag estándar de Failsafe) para confirmar que el proyecto compila y empaqueta sin ejecutar los `*IT.java`.
- La verificación real de los tests con Testcontainers llega solo cuando se hace push y corre `examples-ci.yml` en GitHub Actions (runners `ubuntu-latest`, Docker preinstalado, sin cambios necesarios al workflow más allá de añadir `06-testing` a la matriz).
- Esto es una decisión ya aceptada explícitamente por el usuario al brainstormear esta fase, no una limitación a resolver ahora.

## Fuera de alcance

- Mutation testing (PIT o similar).
- Tests de carga/performance (JMeter, Gatling).
- Contract testing (Pact o similar).
- Cambios al workflow de CI más allá de añadir `06-testing` a la matriz existente de `examples-ci.yml`.
- Cobertura de código vía JaCoCo u otra herramienta — no se pidió y no es el foco pedagógico de la fase.
- Optimización de contenedores Testcontainers (reutilización de contenedores entre clases de test, "singleton container pattern") — mencionable como nota al margen en la página 3, pero sin implementarlo.
- Refresh tokens/OAuth2/OIDC (ya fuera de alcance desde Fase 3), cachear `findAll` (ya fuera de alcance desde Fase 4).

## Verificación / criterios de aceptación

- Build de Docusaurus sin enlaces rotos.
- `./mvnw test` pasa en verde localmente en `examples/06-testing` (JUnit avanzado + Mockito, sin Docker).
- `./mvnw verify -DskipITs` compila y empaqueta sin errores localmente.
- `./mvnw verify` (con los `*IT.java` de Testcontainers) pasa en verde en GitHub Actions tras el push — **gate explícito antes de dar la fase por cerrada**, no basta con la verificación local.
- Las 4 páginas de contenido existen, en español, con código consistente con el ejemplo real.
- La página de Mockito lidera con el criterio de decisión y errores comunes antes de mostrar código, no al revés.
- Sidebar en orden correcto: JUnit avanzado → Mockito y tests unitarios → Testcontainers → página de síntesis.
- `mvnw` commiteado con bit de ejecución correcto.
- `pom.xml` sin boilerplate vacío de Initializr, con `maven-failsafe-plugin` correctamente enlazado.
- El test de ownership con Mockito (`TaskServiceImplTest`) cubre 403 (tarea ajena) y 404 (tarea inexistente) sin contexto Spring ni base de datos.
- El test `*IT.java` con Testcontainers ejercita Postgres y Redis reales, no H2/`ConcurrentMapCacheManager`.

## Pendientes antes de implementar

1. ~~Aprobación explícita del usuario de este diseño~~ — **hecho, 2026-09-23**.
2. Invocar `writing-plans` para el plan de implementación de la Fase 6.
