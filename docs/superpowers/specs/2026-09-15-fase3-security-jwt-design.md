# Fase 3 — Security/JWT (diseño)

> Estado: **diseño aprobado por el usuario el 2026-09-15**. Siguiente paso: invocar `writing-plans` para el plan de implementación.

## Contexto

Continuación del roadmap tras la Fase 2 (Persistencia, completa). La Fase 3 añade autenticación y autorización al API de tareas: login con JWT propio (emitido por la propia app, sin proveedor externo), roles (`USER`/`ADMIN`) y ownership de recursos.

Decisiones ya cerradas que aplican aquí (ver [[spring-boot-tech-decisions]] y las specs de Fases 0-2): Docusaurus, contenido en español, ejemplos ejecutables autocontenidos en Maven + Java 21, un ejemplo por fase en `examples/`, CI vía GitHub Actions con matriz.

**Lecciones técnicas heredadas de las Fases 0-2 (aplicar desde el diseño del plan, no corregirlas después):**
1. No fijar la versión de Spring Boot al generar el ejemplo — omitir `bootVersion` en la llamada a Initializr.
2. Verificar explícitamente el bit de ejecución de `mvnw` antes de cada commit (`git update-index --chmod=+x` + `git ls-files -s` mostrando `100755`).
3. Limpiar el `pom.xml` generado por Initializr (quitar `<description/>`, `<url/>`, `<licenses>`, `<developers>`, `<scm>` vacíos) como paso explícito del plan.
4. Spring Boot 4.x cambió paquetes conocidos respecto a la 3.x (`@AutoConfigureMockMvc` en `org.springframework.boot.webmvc.test.autoconfigure`, nombres de starters `-webmvc`, Jackson bajo `tools.jackson`) — verificar la realidad del proyecto generado, no asumir que sigue igual que en fases anteriores.
5. `examples/03-security-jwt` es un proyecto Maven **completamente independiente y nuevo**; no se modifica `examples/02-persistencia`. Al reutilizar nombres de clases de fases previas (`Task`, `TaskController`, etc.), no hay colisión posible porque nunca comparten classpath.
6. `spring.jpa.hibernate.ddl-auto: validate` en ambos perfiles (main y test) — el esquema lo gestiona exclusivamente Flyway, igual que en Fase 2.
7. Tests con H2 en `src/test/resources/application.yml` (sin `@ActiveProfiles`, recogido automáticamente por Spring Boot) — sin necesitar Docker, igual que en Fase 2.

## Continuidad pedagógica

**Mismo dominio que las Fases 1-2:** gestor de tareas. El eje pedagógico de esta fase es "el mismo API de tareas, ahora protegido" — se reintroducen `TaskController`/`TaskService`/`TaskServiceImpl`/`ApiError`/`GlobalExceptionHandler` casi sin cambios respecto a la Fase 2 (no son el foco, se presentan como ya conocidos), y se añade encima todo lo nuevo: usuarios, login, JWT, roles y ownership.

`examples/03-security-jwt` es un proyecto Maven **independiente y autocontenido**, no una modificación de `examples/02-persistencia`.

## Estructura de contenido

`docs-site/docs/03-security-jwt/` (el `_category_.json` con `position: 5` ya existe de la Fase 0, no se toca):

- `index.md` — deja de ser el placeholder "en construcción"; introduce la fase con el contraste explícito "hasta ahora cualquiera podía usar el API, ahora hace falta autenticarse", y enlaza a las 4 sub-páginas.
- 4 páginas, una por subtema:
  1. **Spring Security básico** — qué cambia nada más añadir el starter (todo bloqueado por defecto), `SecurityFilterChain`, `PasswordEncoder`/BCrypt, `UserDetailsService` respaldado por una entidad `User` real.
  2. **Autenticación con JWT** — ciclo completo de un JWT propio: registro, login, cómo se firma el token (jjwt, HMAC), y el filtro que lo valida en cada petición protegida.
  3. **Roles y autorización** — `USER` vs `ADMIN`, ownership de tareas a nivel de servicio, `@PreAuthorize` sobre un endpoint admin-only, y respuestas JSON consistentes en 401/403.
  4. **Probar endpoints protegidos** — walkthrough con `curl` (registrar → login → usar el token) y cómo los tests obtienen un JWT real en vez de mockear la autenticación.

## Ejemplo ejecutable: `examples/03-security-jwt`

**Nombres:** groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks` (mismo paquete que fases anteriores, mismo motivo narrativo — sin colisión posible, proyectos Maven independientes), artifactId `tasks-security`, `name=TasksSecurity`, carpeta `examples/03-security-jwt`.

**Dependencias nuevas sobre las de Fase 2** (`web`, `validation`, `data-jpa`, `postgresql`, `flyway`, `h2`): `security` (Spring Initializr) + `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (añadidas a mano al `pom.xml` tras generar el proyecto, Initializr no las ofrece).

### Modelo de datos

- `User` (`@Entity`, tabla `users`): `id`, `username` (`@Column(unique = true)`), `password` (hash BCrypt), `role` (`enum Role { USER, ADMIN }`, mapeado con `@Enumerated(EnumType.STRING)`).
- `Task`: se le añade `@ManyToOne private User user` (columna `user_id`, `NOT NULL`) — no un `Long` suelto, para poder usar `TaskRepository.findByUser(User user)` (`USER` viendo solo las suyas) y `findAll()` (`ADMIN` viendo todas) sin escribir SQL a mano, coherente con el enfoque "cero implementación" de `JpaRepository` ya establecido en Fase 2.

**Migraciones Flyway** (proyecto nuevo, sin arrastrar nada de Fase 2):
- `V1__create_users_table.sql` — tabla `users` (id, username único, password, role).
- `V2__create_tasks_table.sql` — tabla `tasks` (id, titulo, descripcion, completada, `user_id NOT NULL REFERENCES users(id)`).
- `V3__seed_admin_user.sql` — inserta un usuario `admin`/rol `ADMIN` con una contraseña conocida, ya hasheada con BCrypt (el hash se pre-calcula fuera de la app — p.ej. con un `main()` de un solo uso o el propio `PasswordEncoder` en un test/REPL — y se pega como literal en el `INSERT`, porque SQL no puede invocar el encoder de la app). Las credenciales del admin seed se documentan en el `README.md` del ejemplo.

Mismo criterio de compatibilidad Postgres/H2 que la Fase 2: SQL estándar, sin tipos específicos de un solo motor.

### Endpoints

Nuevos, bajo `AuthController`:
- `POST /auth/register` — body `RegisterRequest { username, password }`, ambos `@NotBlank`, `password` además `@Size(min = 8)`. Crea un `User` con rol `USER` (password hasheada con `PasswordEncoder`). `400` si falla la validación; `409 Conflict` si el username ya existe.
- `POST /auth/login` — body `LoginRequest { username, password }`, ambos `@NotBlank` (sin `@Size` aquí: no se valida la forma de una contraseña que ya existe, solo que no venga vacía). Valida contra la BD; `200` con `{ "token": "<jwt>" }` si es correcto, `401` si no (usuario inexistente y contraseña incorrecta devuelven el mismo `401` genérico, para no revelar qué username existe).

Existentes (`/tasks/**`, reintroducidos desde Fase 2 sin cambios de forma) pasan a requerir `Authorization: Bearer <token>`:
- `POST /tasks` — el `user_id` de la tarea creada es siempre el del usuario autenticado (se ignora cualquier valor de propietario que llegara en el body — este endpoint no lo expone).
- `GET /tasks` — `USER` recibe solo sus propias tareas; `ADMIN` recibe todas.
- `GET/PUT/DELETE /tasks/{id}` — `USER` solo puede operar sobre una tarea si es su dueño (si no, `403 Forbidden`, no `404`, para no filtrar si el recurso existe); `ADMIN` puede operar sobre cualquiera.

Nuevo, admin-only — ejemplo concreto de autorización declarativa por rol (no ownership):
- `GET /admin/users` — lista todos los usuarios (sin exponer el hash de password en la respuesta), protegido con `@PreAuthorize("hasRole('ADMIN')")`.

### Componentes de seguridad

- `SecurityConfig` (`@Configuration`, bean `SecurityFilterChain`): sesiones stateless (`SessionCreationPolicy.STATELESS`), CSRF desactivado (API sin sesión ni cookies), `/auth/**` público, resto autenticado, `@EnableMethodSecurity` para que `@PreAuthorize` funcione.
- `JwtService`: genera el token (subject = username, claim de rol, expiración corta — p. ej. 1 hora), lo firma con una clave HMAC (de configuración, `application.yml`), y lo valida/parsea.
- `JwtAuthenticationFilter` (`OncePerRequestFilter`): lee el header `Authorization`, extrae y valida el token con `JwtService`, carga el `UserDetails` y puebla el `SecurityContextHolder`. Registrado con `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`.
- `UserDetailsServiceImpl`: implementa `UserDetailsService`, carga `User` vía `UserRepository` y lo adapta a `UserDetails` (rol como `GrantedAuthority` con prefijo `ROLE_`).
- `CustomAuthenticationEntryPoint` / `CustomAccessDeniedHandler`: sustituyen el comportamiento por defecto de Spring Security (redirect/HTML) por respuestas JSON con el mismo formato `ApiError` que ya usa el `GlobalExceptionHandler`, para 401 y 403 respectivamente.

### Autorización (roles y ownership)

La comprobación de ownership vive en `TaskServiceImpl`, no en el controller ni vía `@PreAuthorize` sobre el id de la tarea — decidir "propia vs ajena" requiere cargar primero la tarea y compararla contra el usuario autenticado (obtenido vía `SecurityContextHolder`/`@AuthenticationPrincipal`). `@PreAuthorize("hasRole('ADMIN')")` se reserva para `GET /admin/users`, un caso real y limpio de autorización declarativa por rol, para no forzar su uso donde no encaja (la lógica de `/tasks` es más rica que "solo un rol", así que vive en código explícito en el service).

### Manejo de errores

Extiende el `GlobalExceptionHandler`/`ApiError`/`ValidationApiError` reintroducidos desde Fase 1-2:
- `401 Unauthorized` — login con credenciales inválidas; token ausente, inválido o expirado en un endpoint protegido.
- `403 Forbidden` — autenticado pero sin permiso (tarea ajena siendo `USER`; `USER` intentando `GET /admin/users`).
- `409 Conflict` — username ya registrado.
- `400 Bad Request` — validación de `RegisterRequest`/`LoginRequest` (reutiliza `ValidationApiError`).
- `404 Not Found` — se mantiene solo para "la tarea no existe en absoluto" (ni para su dueño ni para nadie); una tarea existente pero ajena para un `USER` es `403`, nunca `404`.

### Tests

Tests de integración con `MockMvc`, **sin `@WithMockUser`**: cada test que necesita autenticación registra un usuario (o usa el admin seed), hace login para obtener un JWT real, y lo usa en el header `Authorization` de las peticiones siguientes — se prueba el flujo completo tal como lo usaría un cliente real, no una autenticación simulada por el framework de test. Perfil `test` con H2 en memoria (igual que Fase 2), sin Docker.

Casos mínimos a cubrir (el plan de implementación detalla la lista TDD completa): registro válido/username duplicado, login válido/inválido, acceso a `/tasks` sin token (401), acceso con token expirado/manipulado (401), `USER` accediendo a tarea ajena (403), `ADMIN` accediendo a tarea ajena (200), `USER` en `GET /admin/users` (403), `ADMIN` en `GET /admin/users` (200).

### Infraestructura

Mismo patrón que Fases 0-2: proyecto generado vía Spring Initializr (sin fijar `bootVersion`), Java 21, Maven Wrapper con bit de ejecución verificado explícitamente antes del commit, `pom.xml` limpiado de boilerplate vacío de Initializr como paso explícito del plan, `docker-compose.yml` propio (mismo patrón que Fase 2: Postgres 16, volumen nombrado, puerto **`5434`** en el host — para no chocar con el `5433` ya usado por `02-persistencia` si ambos se levantan a la vez), `README.md` con instrucciones (incluyendo las credenciales del admin seed). Se añade `03-security-jwt` a la matriz de `examples-ci.yml` — el job de CI no necesita un servicio Postgres porque los tests usan H2.

**Docker sigue sin estar disponible en el entorno donde se implementa esta fase** (confirmado en Fase 2) — mismo tratamiento: verificación limitada a sintaxis de `docker-compose.yml`, verificación real contra Postgres queda como pendiente explícito para el usuario.

## Fuera de alcance

- Refresh tokens, logout/blacklist de tokens — JWT sin estado, se documenta como limitación conocida.
- OAuth2/OIDC, proveedores externos (Keycloak, Auth0, etc.).
- Recuperación de contraseña, verificación de email, rate limiting de login/registro.
- CORS — ninguna fase del roadmap tiene todavía un frontend real que lo necesite.
- Testcontainers (Fase 6, ya establecido en Fase 2).

## Verificación / criterios de aceptación

- Build de Docusaurus sin enlaces rotos.
- `mvn verify` pasa en `examples/03-security-jwt` usando H2 (sin necesitar Docker), local y en CI, cubriendo los casos de la sección de Tests.
- Las 4 páginas de contenido existen, en español, con código consistente con el ejemplo real.
- Revisión del sidebar: la Fase 3 muestra sus 4 sub-páginas en el menú, en el orden correcto (Spring Security básico → Autenticación con JWT → Roles y autorización → Probar endpoints protegidos).
- `mvnw` commiteado con bit de ejecución correcto (verificado explícitamente, no dado por hecho).
- `pom.xml` sin boilerplate vacío de Initializr.
- `docker-compose.yml` sintácticamente válido; verificación real contra Postgres documentada como pendiente explícito (igual que Fase 2).
- Ningún endpoint de `/tasks` accesible sin token válido; `403` (no `404`) al acceder a una tarea ajena siendo `USER`.

## Pendientes antes de implementar

1. ~~Aprobación explícita del usuario de este diseño~~ — **hecho, 2026-09-15**.
2. Invocar `writing-plans` para el plan de implementación de la Fase 3.
