# Fase 4 — Caché y Redis — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir caché al API de tareas de la Fase 3 — Spring Cache (abstracción) con Redis como backend real y `ConcurrentMapCacheManager` para tests/desarrollo sin Docker — junto con un nuevo ejemplo ejecutable (`examples/04-cache-redis`) y 4 páginas de contenido nuevas.

**Architecture:** Mismo patrón que las Fases 0-3: contenido Docusaurus en `docs-site/docs/04-cache-redis/` (categoría ya existente) más un proyecto Maven/Spring Boot independiente en `examples/04-cache-redis/`, añadido a la matriz de `examples-ci.yml`. El ejemplo reintroduce el API de tareas de la Fase 3 (auth JWT, roles, ownership) casi sin cambios y le añade caché sobre las lecturas de tareas: un bean separado (`CachedTaskLookup`) evita el problema clásico de auto-invocación de proxies AOP, y el servicio siempre re-verifica el permiso de acceso aunque el dato venga de caché.

**Tech Stack:** Spring Boot (versión estable actual de `start.spring.io`, sin fijar un número — 4.1.1 en las Fases 0-3), Java 21, Maven Wrapper, Spring Security, `io.jsonwebtoken` (jjwt) 0.12.6, Spring Data JPA, PostgreSQL, Flyway, H2 (tests/perfil sin Docker), Spring Cache + Spring Data Redis, Redis 7, MockMvc.

**Spec:** [2026-09-15-fase4-cache-redis-design.md](../specs/2026-09-15-fase4-cache-redis-design.md)

## Global Constraints

- Idioma del contenido: **español**; código e identificadores en inglés/sin acentos.
- `examples/04-cache-redis` es un proyecto Maven **independiente y autocontenido** — no modifica `examples/03-security-jwt`.
- **No fijar la versión de Spring Boot.** Omitir `bootVersion` en la llamada a Initializr.
- **El bit de ejecución de `mvnw` debe verificarse explícitamente antes de cada commit** (`git update-index --chmod=+x mvnw` + `git ls-files -s` mostrando `100755`).
- **Limpiar el `pom.xml` generado por Initializr** de boilerplate vacío (`<description/>`, `<url/>`, `<licenses>`, `<developers>`, `<scm>`) como paso explícito de Task 1.
- Nombres: groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks` (mismo paquete que Fases 1-3), artifactId `tasks-cache`, carpeta `examples/04-cache-redis`, clase principal `TasksCacheApplication` (de `name=TasksCache`).
- `spring.jpa.hibernate.ddl-auto: validate` en todos los perfiles (main, test, h2) — el esquema lo gestiona exclusivamente Flyway.
- Puerto de Postgres en el host: `5435` (Fase 2 usa `5433`, Fase 3 usa `5434`). Redis usa su puerto por defecto `6379` sin remapear.
- **Docker no está disponible en este entorno.** Ninguna tarea puede verificar `docker compose up` contra Postgres/Redis reales — la verificación de `docker-compose.yml` se limita a validar su sintaxis YAML. Queda como pendiente explícito para el usuario (Task 8 lo documenta), igual que en Fases 2-3.
- Máquina de desarrollo: usar `JAVA_HOME=/c/jdk-23.0.1` (bash) para toda invocación de `./mvnw`.
- `io.jsonwebtoken` (jjwt) no está en el BOM de Spring Boot — versión fijada explícitamente en **0.12.6** (misma ya verificada en Fase 3). Si al implementar existe una versión más reciente, usar esa.
- Tests de integración usan **JWT reales obtenidos vía `/auth/register` + `/auth/login`** — nunca `@WithMockUser` ni autenticación simulada.
- **Spring Data Redis 4.x (el que empareja con Spring Boot 4.1.1/Spring Framework 7) deprecó `GenericJackson2JsonRedisSerializer` en favor de `GenericJacksonJsonRedisSerializer`**, nativo de Jackson 3 (`tools.jackson.*`). A diferencia del serializador antiguo, el nuevo **no** activa "default typing" (el mecanismo que permite reconstruir el tipo concreto al deserializar un `Object` genérico) por defecto — hay que activarlo explícitamente vía `GenericJacksonJsonRedisSerializer.builder().enableDefaultTyping(typeValidator)`, con un `PolymorphicTypeValidator` (`tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator`) acotado al paquete propio (`allowIfSubType("dev.springbootdocs.examples.tasks.")`) — nunca `enableUnsafeDefaultTyping()` sin validador, que acepta cualquier clase del classpath. Confirmado por la documentación oficial de migración de Spring Data Redis el día de este plan; si al implementar la API difiere, adaptarse a la realidad del proyecto generado (mismo criterio que con `bootVersion`), documentando el cambio.
- **`RedisCacheManagerBuilderCustomizer` vive en `org.springframework.boot.cache.autoconfigure`** en Spring Boot 4.x (no en `org.springframework.boot.autoconfigure.cache`, el paquete de Boot 2.x/3.x) — verificar el import real al compilar.
- **Ojo con el choque de nombres:** existen dos clases distintas llamadas `RedisCacheConfiguration` — la de autoconfiguración de Spring Boot (`org.springframework.boot.autoconfigure.cache`, no se usa en este plan) y la de Spring Data Redis (`org.springframework.data.redis.cache.RedisCacheConfiguration`, la que sí se usa aquí para `entryTtl`/`serializeValuesWith`). Importar siempre la segunda.
- Fuera de alcance: cachear `findAll`, invalidación distribuida/Redis Cluster/Sentinel/Pub-Sub, métricas de caché vía Actuator, patrones write-through/refresh-ahead, cache stampede, Redis como cola/sesión, refresh tokens, OAuth2/OIDC, CORS, Testcontainers (Fase 6).

---

## File Structure

```
docs-site/docs/04-cache-redis/
├── _category_.json                     # ya existe, no tocar
├── index.md                            # reescrito
├── spring-cache-basico.md              # nuevo
├── redis-como-backend.md               # nuevo
├── invalidacion-de-cache.md            # nuevo
└── probar-con-cache-real.md            # nuevo

examples/04-cache-redis/                # nuevo proyecto Maven (vía Spring Initializr)
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/...
├── docker-compose.yml                  # postgres (5435) + redis:7-alpine
├── src/main/java/dev/springbootdocs/examples/tasks/
│   ├── TasksCacheApplication.java      # generado por Initializr
│   ├── Role.java                       # reutilizado de Fase 3
│   ├── User.java                       # reutilizado
│   ├── UserRepository.java             # reutilizado
│   ├── UserPrincipal.java              # reutilizado
│   ├── UserDetailsServiceImpl.java     # reutilizado
│   ├── RegisterRequest.java            # reutilizado
│   ├── LoginRequest.java               # reutilizado
│   ├── AuthResponse.java               # reutilizado
│   ├── UsernameAlreadyExistsException.java  # reutilizado
│   ├── AuthController.java             # reutilizado
│   ├── JwtService.java                 # reutilizado
│   ├── JwtAuthenticationFilter.java    # reutilizado
│   ├── SecurityConfig.java             # reutilizado
│   ├── CustomAuthenticationEntryPoint.java  # reutilizado
│   ├── CustomAccessDeniedHandler.java  # reutilizado
│   ├── ApiError.java                   # reutilizado
│   ├── ValidationApiError.java         # reutilizado
│   ├── GlobalExceptionHandler.java     # reutilizado, luego modificado (Task 3)
│   ├── Task.java                       # reutilizado + anotación defensiva Jackson
│   ├── TaskRequest.java                # reutilizado
│   ├── TaskResponse.java               # reutilizado
│   ├── TaskNotFoundException.java      # reutilizado
│   ├── TaskAccessDeniedException.java  # reutilizado
│   ├── TaskRepository.java             # reutilizado
│   ├── CachedTaskLookup.java           # NUEVO — foco pedagógico de esta fase
│   ├── TaskService.java                # reutilizado
│   ├── TaskServiceImpl.java            # MODIFICADO — usa CachedTaskLookup + @CacheEvict
│   ├── TaskController.java             # reutilizado
│   ├── UserSummary.java                # reutilizado
│   ├── AdminController.java            # reutilizado
│   └── CacheConfig.java                # NUEVO — @EnableCaching (Task 3) + RedisCacheManager (Task 4)
├── src/main/resources/
│   ├── application.yml                 # datasource Postgres real (5435), JWT secret
│   ├── application-h2.yml              # perfil sin Docker: H2 archivo + cache simple
│   └── db/migration/
│       ├── V1__create_users_table.sql  # copiada de Fase 3
│       ├── V2__create_tasks_table.sql  # copiada de Fase 3
│       └── V3__seed_admin_user.sql     # regenerada (hash nuevo, mismo usuario/contraseña)
├── src/test/resources/
│   └── application.yml                 # datasource H2, spring.cache.type: simple
├── src/test/java/dev/springbootdocs/examples/tasks/
│   ├── TasksCacheApplicationTests.java # generado por Initializr
│   ├── JwtServiceTest.java             # reutilizado
│   ├── GlobalExceptionHandlerTest.java # reutilizado
│   ├── AuthControllerTest.java         # reutilizado
│   ├── TaskControllerTest.java         # reutilizado
│   ├── CacheBehaviorTest.java          # NUEVO — hit/evict + ownership-en-cache-hit
│   └── AdminControllerTest.java        # reutilizado
└── README.md

.github/workflows/examples-ci.yml       # modificado: +1 línea en matrix.example
```

---

### Task 1: Bootstrap de `examples/04-cache-redis`

**Files:**
- Create: `examples/04-cache-redis/` (scaffold vía Spring Initializr)
- Modify: `examples/04-cache-redis/pom.xml` (limpieza + dependencias jjwt)
- Create: `examples/04-cache-redis/docker-compose.yml`
- Create: `examples/04-cache-redis/src/main/resources/db/migration/V1__create_users_table.sql`
- Create: `examples/04-cache-redis/src/main/resources/db/migration/V2__create_tasks_table.sql`
- Create: `examples/04-cache-redis/src/main/resources/db/migration/V3__seed_admin_user.sql`
- Create: `examples/04-cache-redis/src/main/resources/application.yml`
- Create: `examples/04-cache-redis/src/main/resources/application-h2.yml`
- Create: `examples/04-cache-redis/src/test/resources/application.yml`
- Delete: `examples/04-cache-redis/src/main/resources/application.properties`

**Interfaces:**
- Produces: proyecto Maven con wrapper funcional, esquema `users`+`tasks` creado por Flyway (incluido el usuario `admin` semilla), listo para que Task 2 añada las clases Java.

- [ ] **Step 1: Generar el proyecto base desde Spring Initializr (sin fijar `bootVersion`)**

```bash
mkdir -p /d/Proyectos/spring-boot/examples/04-cache-redis
cd /d/Proyectos/spring-boot/examples
curl https://start.spring.io/starter.zip \
  -d dependencies=web,validation,data-jpa,postgresql,flyway,h2,security,cache,data-redis \
  -d type=maven-project \
  -d language=java \
  -d javaVersion=21 \
  -d groupId=dev.springbootdocs.examples \
  -d artifactId=tasks-cache \
  -d name=TasksCache \
  -d packageName=dev.springbootdocs.examples.tasks \
  -o tasks-cache.zip
```

- [ ] **Step 2: Descomprimir en la carpeta final y limpiar el zip**

```bash
cd 04-cache-redis
unzip -o ../tasks-cache.zip
rm ../tasks-cache.zip
```

- [ ] **Step 3: Verificar que el wrapper funciona**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -v
```

Expected: imprime versión de Apache Maven y un JDK 21+ sin errores. Si el `pom.xml` generado usa nombres de dependencia distintos a los esperados (por ejemplo `spring-boot-starter-webmvc` en vez de `-web`, como ya ocurrió en Fase 3), no es un error — adaptarse a la realidad del proyecto generado.

- [ ] **Step 4: Corregir el bit de ejecución de `mvnw` — antes de cualquier otro paso**

```bash
cd /d/Proyectos/spring-boot
git add examples/04-cache-redis
git update-index --chmod=+x examples/04-cache-redis/mvnw
git ls-files -s examples/04-cache-redis/mvnw
```

Expected: la última línea muestra el modo `100755`. Si muestra `100644`, repetir hasta confirmarlo.

- [ ] **Step 5: Limpiar el `pom.xml` de boilerplate vacío de Initializr y añadir las dependencias de jjwt**

Abrir `examples/04-cache-redis/pom.xml`. Eliminar `<url/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>`, el `<scm>` vacío, y sustituir `<description/>` por:

```xml
<description>API REST de gestión de tareas con caché (Spring Cache + Redis), protegida con Spring Security y JWT propio.</description>
```

Añadir la propiedad de versión de jjwt dentro de `<properties>`:

```xml
<jjwt.version>0.12.6</jjwt.version>
```

Añadir estas tres dependencias dentro de `<dependencies>`, junto a las generadas por Initializr (que ya incluyen `spring-boot-starter-cache` y `spring-boot-starter-data-redis`, sin necesidad de versión — gestionadas por el BOM de Spring Boot):

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
```

No tocar `groupId`, `artifactId`, `version`, `name`, `parent`, `properties.java.version` ni el resto de `dependencies`/`build`.

- [ ] **Step 6: Convertir `application.properties` a `application.yml` (perfil principal, Postgres + Redis reales)**

```bash
rm src/main/resources/application.properties
```

Crear `src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: 04-cache-redis
  datasource:
    url: jdbc:postgresql://localhost:5435/tasks_cache
    username: tasks_cache
    password: tasks_cache
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate

app:
  jwt:
    secret: "${JWT_SECRET:local-dev-secret-please-change-in-production-0123456789abcdef}"
    expiration-millis: 3600000
```

No se añade ningún bloque `spring.data.redis.*`: el autoconfig de Spring Boot ya apunta a `localhost:6379` por defecto, que es justo lo que expone `docker-compose.yml` (Step 12) sin remapear.

- [ ] **Step 7: Crear el perfil `h2` (sin Docker, sin Redis)**

Crear `src/main/resources/application-h2.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/tasks_cache;MODE=PostgreSQL
    username: sa
    password:
  cache:
    type: simple
```

`spring.cache.type: simple` fuerza el `ConcurrentMapCacheManager` autoconfigurado por Spring Boot en vez de `RedisCacheManager` — así este perfil corre de punta a punta sin Redis, igual que sin Postgres (usa H2 en archivo, mismo patrón que Fases 2-3).

- [ ] **Step 8: Crear el perfil de test (H2 en memoria + caché simple)**

Crear `src/test/resources/application.yml`:
```yaml
spring:
  application:
    name: 04-cache-redis
  datasource:
    url: jdbc:h2:mem:tasks_cache;MODE=PostgreSQL
    username: sa
    password:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
  cache:
    type: simple

app:
  jwt:
    secret: "test-secret-key-for-integration-tests-only-0123456789abcdef"
    expiration-millis: 3600000
```

Nota: `spring-boot-starter-data-redis` sigue en el classpath de test (no hay forma de excluirlo solo para test sin complicar el `pom.xml`), así que Spring Boot igualmente crea un bean `LettuceConnectionFactory` apuntando a `localhost:6379` por defecto. Esto es seguro porque Lettuce no abre una conexión de red al crear el bean (`afterPropertiesSet()` no valida la conexión por defecto) y, con `spring.cache.type: simple`, ningún componente de caché llega a usar ese `ConnectionFactory` — se confirma empíricamente en el Step 14 de este mismo Task (el contexto debe arrancar sin Redis corriendo). Si el arranque fallara por un intento real de conexión a Redis, la mitigación es excluir la autoconfiguración de Redis en este perfil de test con `spring.autoconfigure.exclude`, apuntando al nombre real de la clase de autoconfiguración de Redis en el classpath generado (verificarlo, no asumirlo — mismo criterio que con `bootVersion`).

- [ ] **Step 9: Copiar las migraciones de `users` y `tasks` desde la Fase 3 (sin cambios de esquema)**

Crear `src/main/resources/db/migration/V1__create_users_table.sql`:
```sql
CREATE TABLE users (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL
);
```

Crear `src/main/resources/db/migration/V2__create_tasks_table.sql`:
```sql
CREATE TABLE tasks (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    descripcion VARCHAR(1000),
    completada BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL REFERENCES users(id)
);
```

- [ ] **Step 10: Generar el hash BCrypt del admin seed y crear `V3__seed_admin_user.sql`**

Crear temporalmente `src/main/java/dev/springbootdocs/examples/tasks/GenerateAdminHash.java`:

```java
package dev.springbootdocs.examples.tasks;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateAdminHash {

    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode("admin12345"));
    }
}
```

Ejecutarlo apoyándose en el plugin de Spring Boot ya presente en el `pom.xml`:

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -q spring-boot:run -Dspring-boot.run.main-class=dev.springbootdocs.examples.tasks.GenerateAdminHash
```

Expected: imprime una única línea con un hash BCrypt, por ejemplo `$2a$10$........................................`. Copiar ese valor exacto.

Crear `src/main/resources/db/migration/V3__seed_admin_user.sql`, sustituyendo `<HASH>` por el valor copiado:

```sql
INSERT INTO users (username, password, role)
VALUES ('admin', '<HASH>', 'ADMIN');
```

Borrar el archivo temporal:

```bash
rm src/main/java/dev/springbootdocs/examples/tasks/GenerateAdminHash.java
```

Nota: BCrypt genera una sal aleatoria en cada ejecución — el hash exacto no es reproducible ni importa que lo sea, solo que `BCryptPasswordEncoder.matches("admin12345", hash)` sea `true`, confirmado indirectamente en Task 2 (`AuthControllerTest`, login como admin) y explícitamente en Task 5 (`AdminControllerTest`).

- [ ] **Step 11: Crear `docker-compose.yml` con Postgres y Redis**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: tasks_cache
      POSTGRES_USER: tasks_cache
      POSTGRES_PASSWORD: tasks_cache
    ports:
      - "5435:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  postgres-data:
```

Redis no lleva volumen: es una caché, no una fuente de verdad — perderla en un reinicio del contenedor es aceptable y, de hecho, coherente con lo que se enseña en esta fase.

- [ ] **Step 12: Validar la sintaxis YAML de `docker-compose.yml`**

```bash
npx -y js-yaml docker-compose.yml
```

Expected: imprime el YAML parseado sin errores. (Docker no está disponible en este entorno — ver Global Constraints.)

- [ ] **Step 13: Confirmar que el test de contexto por defecto pasa usando H2 — ejercita las 3 migraciones reales**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`. El único test en este punto es `TasksCacheApplicationTests.contextLoads`, pero Flyway ya aplica `V1`, `V2` y `V3` contra H2 en modo PostgreSQL, y el contexto arranca con `spring-boot-starter-data-redis` en el classpath sin que haya un Redis corriendo — confirma empíricamente la nota del Step 8 sobre `LettuceConnectionFactory`. Si falla por el `INSERT` de `V3`, revisar el contenido exacto del archivo; si falla por un intento de conexión a Redis, aplicar la mitigación del Step 8.

- [ ] **Step 14: Commit**

```bash
git add examples/04-cache-redis
git commit -m "chore: bootstrap examples/04-cache-redis (users/tasks schema, Postgres, Redis, Flyway, H2 tests)"
```

---

### Task 2: Puerto de autenticación JWT (reutilizado de Fase 3, sin cambios de diseño)

Esta fase no rediseña autenticación/roles — el spec es explícito: "se reintroducen `User`/`Role`/`AuthController`/`JwtService`/`JwtAuthenticationFilter`/`SecurityConfig`/... de la Fase 3 casi sin cambios (no son el foco)". Este Task porta ese código verbatim a `examples/04-cache-redis` y confirma con los mismos tests que sigue funcionando en el proyecto nuevo — no es un ejercicio de diseño, es una verificación de que el port compila y se comporta igual.

**Files:**
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/Role.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/User.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/UserRepository.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/UserPrincipal.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/UserDetailsServiceImpl.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/RegisterRequest.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/LoginRequest.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/AuthResponse.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/UsernameAlreadyExistsException.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/AuthController.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/JwtService.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/JwtAuthenticationFilter.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/SecurityConfig.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/CustomAuthenticationEntryPoint.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/CustomAccessDeniedHandler.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/ApiError.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/ValidationApiError.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/GlobalExceptionHandler.java`
- Test: `examples/04-cache-redis/src/test/java/dev/springbootdocs/examples/tasks/JwtServiceTest.java`
- Test: `examples/04-cache-redis/src/test/java/dev/springbootdocs/examples/tasks/GlobalExceptionHandlerTest.java`
- Test: `examples/04-cache-redis/src/test/java/dev/springbootdocs/examples/tasks/AuthControllerTest.java`

**Interfaces:**
- Consumes: proyecto de Task 1 (esquema `users`/`tasks`, admin seed).
- Produces: `POST /auth/register`, `POST /auth/login` (usados por Task 3 y Task 5 para obtener tokens reales en sus tests). `SecurityConfig` con `@EnableMethodSecurity` activo (lo usará Task 5). `JwtService.generateToken(String, Role)`, `.isValid(String)`, `.extractUsername(String)`. `UserPrincipal.getUser()` devuelve el `User` completo, usado por Task 3 vía `@AuthenticationPrincipal`.

- [ ] **Step 1: Crear `Role`, `User`, `UserRepository`**

`Role.java`:
```java
package dev.springbootdocs.examples.tasks;

public enum Role {
    USER,
    ADMIN
}
```

`User.java`:
```java
package dev.springbootdocs.examples.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected User() {
    }

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }
}
```

`UserRepository.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
```

- [ ] **Step 2: Crear `UserPrincipal` y `UserDetailsServiceImpl`**

`UserPrincipal.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
}
```

`UserDetailsServiceImpl.java`:
```java
package dev.springbootdocs.examples.tasks;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No existe el usuario " + username));
        return new UserPrincipal(user);
    }
}
```

- [ ] **Step 3: Crear `JwtService` y su test — RED primero**

`JwtServiceTest.java` (test unitario puro, sin `@SpringBootTest`):

```java
package dev.springbootdocs.examples.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef0123456789";

    @Test
    void generateAndValidate_roundTrip() {
        JwtService jwtService = new JwtService(SECRET, 60_000);

        String token = jwtService.generateToken("alice", Role.USER);

        assertTrue(jwtService.isValid(token));
        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void isValid_returnsFalse_whenTokenExpired() {
        JwtService jwtService = new JwtService(SECRET, -1_000);

        String token = jwtService.generateToken("alice", Role.USER);

        assertFalse(jwtService.isValid(token));
    }

    @Test
    void isValid_returnsFalse_whenSignatureTampered() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        String token = jwtService.generateToken("alice", Role.USER);
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        assertFalse(jwtService.isValid(tampered));
    }
}
```

- [ ] **Step 4: Ejecutar el test y confirmar que falla (RED)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=JwtServiceTest
```

Expected: FAIL — no compila, `JwtService` no existe todavía.

- [ ] **Step 5: Implementar `JwtService`**

```java
package dev.springbootdocs.examples.tasks;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-millis}") long expirationMillis) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(String username, Role role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

- [ ] **Step 6: Ejecutar el test y confirmar que pasa (GREEN)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=JwtServiceTest
```

Expected: PASS — 3/3 tests.

- [ ] **Step 7: Crear `JwtAuthenticationFilter`**

```java
package dev.springbootdocs.examples.tasks;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                if (jwtService.isValid(token)) {
                    String username = jwtService.extractUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (UsernameNotFoundException ignored) {
                // el token es válido pero el usuario ya no existe: se trata como no autenticado
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 8: Crear `ApiError`, `ValidationApiError`, `CustomAuthenticationEntryPoint` y `CustomAccessDeniedHandler`**

`ApiError.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.time.Instant;

public record ApiError(int status, String message, String timestamp) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Instant.now().toString());
    }
}
```

`ValidationApiError.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.time.Instant;
import java.util.Map;

public record ValidationApiError(int status, String message, String timestamp, Map<String, String> errors) {

    public static ValidationApiError of(int status, String message, Map<String, String> errors) {
        return new ValidationApiError(status, message, Instant.now().toString(), errors);
    }
}
```

`CustomAuthenticationEntryPoint.java`:
```java
package dev.springbootdocs.examples.tasks;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        ApiError error = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "No autenticado o token inválido");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
```

`CustomAccessDeniedHandler.java`:
```java
package dev.springbootdocs.examples.tasks;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ApiError error = ApiError.of(HttpStatus.FORBIDDEN.value(), "No tienes permiso para realizar esta acción");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
```

Import necesario: `tools.jackson.databind.ObjectMapper` (Jackson 3, no `com.fasterxml.jackson`). Si Initializr sirvió una versión de Spring Boot distinta, verificar el paquete real antes de asumir.

- [ ] **Step 9: Crear `SecurityConfig`**

```java
package dev.springbootdocs.examples.tasks;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 10: Escribir `AuthControllerTest` — RED**

```java
package dev.springbootdocs.examples.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new RegisterRequest(username, password));
    }

    @Test
    void register_returnsCreatedWithToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("alice", "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void register_withBlankUsername_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void register_withShortPassword_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("bob", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void register_withDuplicateUsername_returnsConflict() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("carol", "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("carol", "password123")))
                .andExpect(status().isConflict());
    }

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("dave", "password123")))
                .andExpect(status().isCreated());

        String loginJson = objectMapper.writeValueAsString(new LoginRequest("dave", "password123"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("erin", "password123")))
                .andExpect(status().isCreated());

        String loginJson = objectMapper.writeValueAsString(new LoginRequest("erin", "wrongpassword"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_asSeededAdmin_returnsToken() throws Exception {
        String loginJson = objectMapper.writeValueAsString(new LoginRequest("admin", "admin12345"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }
}
```

- [ ] **Step 11: Ejecutar el test y confirmar que falla (RED)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=AuthControllerTest
```

Expected: FAIL — no compila (`RegisterRequest`, `LoginRequest`, `AuthController` no existen todavía).

- [ ] **Step 12: Implementar `RegisterRequest`, `LoginRequest`, `AuthResponse`, `UsernameAlreadyExistsException`**

```java
package dev.springbootdocs.examples.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8) String password) {
}
```

```java
package dev.springbootdocs.examples.tasks;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
```

```java
package dev.springbootdocs.examples.tasks;

public record AuthResponse(String token) {
}
```

```java
package dev.springbootdocs.examples.tasks;

public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String username) {
        super("Ya existe un usuario con el nombre " + username);
    }
}
```

- [ ] **Step 13: Implementar `AuthController`**

```java
package dev.springbootdocs.examples.tasks;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()), Role.USER);
        userRepository.save(user);
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token));
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado pero no encontrado"));
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token);
    }
}
```

- [ ] **Step 14: Implementar `GlobalExceptionHandler` (incluye el manejo de `DataIntegrityViolationException` desde el principio — lección de Fase 3, no se descubre después)**

```java
package dev.springbootdocs.examples.tasks;

import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleUsernameExists(UsernameAlreadyExistsException ex) {
        ApiError error = ApiError.of(HttpStatus.CONFLICT.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT.value(), "Conflicto de datos: la operación viola una restricción de la base de datos");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        ApiError error = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Usuario o contraseña incorrectos");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ValidationApiError error = ValidationApiError.of(
                HttpStatus.BAD_REQUEST.value(), "Datos de la petición inválidos", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

(Task 3 añade aquí, con `Edit` no `Create`, los handlers de `TaskNotFoundException` y `TaskAccessDeniedException`.)

- [ ] **Step 15: Escribir y ejecutar `GlobalExceptionHandlerTest`**

```java
package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    /**
     * This handler is a global safety net for ANY database constraint violation that
     * slips past application-level validation (e.g. the TOCTOU race in
     * AuthController#register, where two concurrent registrations with the same
     * username can both pass the existsByUsername check before either saves). It
     * verifies the handler maps DataIntegrityViolationException to 409 Conflict with a
     * generic message, instead of an unhandled 500 or a message specific to one caller.
     */
    @Test
    void handleDataIntegrityViolation_returnsConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        DataIntegrityViolationException ex = new DataIntegrityViolationException("unique constraint violation");

        ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getBody().message())
                .isEqualTo("Conflicto de datos: la operación viola una restricción de la base de datos");
    }
}
```

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=AuthControllerTest,GlobalExceptionHandlerTest
```

Expected: PASS — 7/7 `AuthControllerTest` + 1/1 `GlobalExceptionHandlerTest`.

- [ ] **Step 16: Ejecutar la suite completa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, 12 tests en total (3 `JwtServiceTest` + 7 `AuthControllerTest` + 1 `GlobalExceptionHandlerTest` + 1 `contextLoads`).

- [ ] **Step 17: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/04-cache-redis
git commit -m "feat: port authentication (JWT, roles) from Fase 3 to examples/04-cache-redis"
```

---

### Task 3: TDD — API de tareas con caché (foco pedagógico de esta fase)

**Files:**
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/Task.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/TaskRequest.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/TaskResponse.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/TaskNotFoundException.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/TaskAccessDeniedException.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/TaskRepository.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/CachedTaskLookup.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/TaskService.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/TaskServiceImpl.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/TaskController.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/CacheConfig.java`
- Modify: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/GlobalExceptionHandler.java`
- Test: `examples/04-cache-redis/src/test/java/dev/springbootdocs/examples/tasks/TaskControllerTest.java`
- Test: `examples/04-cache-redis/src/test/java/dev/springbootdocs/examples/tasks/CacheBehaviorTest.java`

**Interfaces:**
- Consumes: `POST /auth/register` + `POST /auth/login` de Task 2; `UserPrincipal.getUser()` de Task 2.
- Produces: `POST /tasks`, `GET /tasks`, `GET/PUT/DELETE /tasks/{id}` — usados por Task 7 (docs) como referencia. Cache `"tasks"` con clave `#id` — usado por Task 4 (Redis) para configurar TTL/serialización sobre ese mismo nombre de caché, y por Task 8 (verificación final).

- [ ] **Step 1: Crear `Task`, `TaskRequest`, `TaskResponse`, `TaskNotFoundException`, `TaskAccessDeniedException`, `TaskRepository`**

`Task.java` — con `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` como medida defensiva: cuando `user` (relación `@ManyToOne(LAZY)`) se serializa hacia Redis, Jackson introspecciona el proxy generado por Hibernate, no la clase `User` declarada — sin esta anotación, propiedades sintéticas del proxy podrían filtrarse al JSON cacheado. Es una medida preventiva estándar al combinar entidades JPA con serialización JSON genérica, independiente de si el proxy llega a estar inicializado o no en el momento de cachear:

```java
package dev.springbootdocs.examples.tasks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    private String descripcion;

    @Column(nullable = false)
    private boolean completada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected Task() {
    }

    public Task(String titulo, String descripcion, boolean completada, User user) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.completada = completada;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean isCompletada() {
        return completada;
    }

    public void setCompletada(boolean completada) {
        this.completada = completada;
    }

    public User getUser() {
        return user;
    }
}
```

`TaskRequest.java`:
```java
package dev.springbootdocs.examples.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank @Size(max = 255) String titulo, @Size(max = 1000) String descripcion, boolean completada) {
}
```

`TaskResponse.java`:
```java
package dev.springbootdocs.examples.tasks;

public record TaskResponse(Long id, String titulo, String descripcion, boolean completada, String username) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitulo(),
                task.getDescripcion(),
                task.isCompletada(),
                task.getUser().getUsername());
    }
}
```

`TaskNotFoundException.java`:
```java
package dev.springbootdocs.examples.tasks;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("No existe ninguna tarea con id " + id);
    }
}
```

`TaskAccessDeniedException.java`:
```java
package dev.springbootdocs.examples.tasks;

public class TaskAccessDeniedException extends RuntimeException {

    public TaskAccessDeniedException(Long id) {
        super("No tienes permiso para acceder a la tarea con id " + id);
    }
}
```

`TaskRepository.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUser(User user);
}
```

- [ ] **Step 2: Crear `CacheConfig` con `@EnableCaching`**

```java
package dev.springbootdocs.examples.tasks;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
}
```

Sin esto, `@Cacheable`/`@CacheEvict` no hacen nada — Spring Boot autoconfigura un `CacheManager` (`ConcurrentMapCacheManager` con `spring.cache.type: simple`, o `RedisCacheManager` en su ausencia si `spring-boot-starter-data-redis` está en el classpath), pero la infraestructura AOP que intercepta las llamadas anotadas solo se activa con `@EnableCaching` explícito — a diferencia de otras auto-configuraciones de Spring Boot, esta es opt-in deliberado.

- [ ] **Step 3: Crear `CachedTaskLookup` — el bean que hace que `@Cacheable` funcione de verdad**

```java
package dev.springbootdocs.examples.tasks;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class CachedTaskLookup {

    private final TaskRepository taskRepository;

    public CachedTaskLookup(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Cacheable(value = "tasks", key = "#id")
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
```

Por qué es un `@Component` aparte y no un método de `TaskServiceImpl` que se auto-invoque: las anotaciones de caché de Spring funcionan vía proxy AOP — Spring envuelve el bean real en un proxy que intercepta las llamadas externas. Una llamada `this.metodo()` dentro de la misma clase nunca pasa por ese proxy (la JVM la resuelve directamente sobre `this`), así que `@Cacheable` se ignoraría silenciosamente — sin ningún error, simplemente sin cachear nunca. `CachedTaskLookup` es un bean Spring distinto de `TaskServiceImpl`, así que la llamada `cachedTaskLookup.findById(id)` desde dentro de `TaskServiceImpl` sí atraviesa el proxy real.

Nota sobre excepciones: `TaskNotFoundException` no se cachea — `@Cacheable` solo almacena el valor de retorno cuando el método termina normalmente, nunca cuando lanza una excepción. Una tarea inexistente se vuelve a consultar en la base de datos en cada intento, lo cual es correcto: cachear un "no existe" congelaría ese estado aunque la tarea se creara después con ese mismo id.

- [ ] **Step 4: Escribir `TaskControllerTest` (reutilizado de Fase 3 verbatim — la API pública no cambia) — RED**

```java
package dev.springbootdocs.examples.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String username) throws Exception {
        String password = "password123";
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin12345"))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Long createTask(String token, String titulo) throws Exception {
        String json = objectMapper.writeValueAsString(new TaskRequest(titulo, "desc", false));
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createTask_withoutToken_returnsUnauthorized() throws Exception {
        String json = objectMapper.writeValueAsString(new TaskRequest("Comprar leche", "2 litros", false));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTask_withToken_returnsCreated() throws Exception {
        String token = registerAndLogin("frank");
        String json = objectMapper.writeValueAsString(new TaskRequest("Comprar leche", "2 litros", false));

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Comprar leche"))
                .andExpect(jsonPath("$.username").value("frank"));
    }

    @Test
    void listTasks_onlyReturnsOwnTasks_forRegularUser() throws Exception {
        String graceToken = registerAndLogin("grace");
        String henryToken = registerAndLogin("henry");
        createTask(graceToken, "Tarea de grace");
        createTask(henryToken, "Tarea de henry");

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + graceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Tarea de grace"));
    }

    @Test
    void listTasks_returnsAllTasks_forAdmin() throws Exception {
        String ivyToken = registerAndLogin("ivy");
        createTask(ivyToken, "Tarea de ivy");
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == 'Tarea de ivy')]").exists());
    }

    @Test
    void getTaskById_whenOwner_returnsTask() throws Exception {
        String token = registerAndLogin("jack");
        Long id = createTask(token, "Tarea de jack");

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getTaskById_whenNotOwnerAndNotAdmin_returnsForbidden() throws Exception {
        String ownerToken = registerAndLogin("kate");
        String otherToken = registerAndLogin("liam");
        Long id = createTask(ownerToken, "Tarea de kate");

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTaskById_whenNotOwnerButAdmin_returnsTask() throws Exception {
        String ownerToken = registerAndLogin("mia");
        Long id = createTask(ownerToken, "Tarea de mia");
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getTaskById_whenNotFound_returns404() throws Exception {
        String token = registerAndLogin("noah");

        mockMvc.perform(get("/tasks/{id}", 999999L).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTask_whenOwner_returnsUpdatedTask() throws Exception {
        String token = registerAndLogin("olivia");
        Long id = createTask(token, "Original");
        String updateJson = objectMapper.writeValueAsString(new TaskRequest("Actualizada", "nueva desc", true));

        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Actualizada"))
                .andExpect(jsonPath("$.completada").value(true));
    }

    @Test
    void updateTask_whenNotOwner_returnsForbidden() throws Exception {
        String ownerToken = registerAndLogin("peter");
        String otherToken = registerAndLogin("quinn");
        Long id = createTask(ownerToken, "Tarea de peter");
        String updateJson = objectMapper.writeValueAsString(new TaskRequest("Hackeada", null, false));

        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTask_whenOwner_thenReturns404OnFollowUpGet() throws Exception {
        String token = registerAndLogin("rachel");
        Long id = createTask(token, "Tarea de rachel");

        mockMvc.perform(delete("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_whenNotOwner_returnsForbidden() throws Exception {
        String ownerToken = registerAndLogin("steve");
        String otherToken = registerAndLogin("tina");
        Long id = createTask(ownerToken, "Tarea de steve");

        mockMvc.perform(delete("/tasks/{id}", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTask_withTituloOverMaxLength_returnsBadRequest() throws Exception {
        String token = registerAndLogin("victor");
        String tituloTooLong = "a".repeat(256);
        String json = objectMapper.writeValueAsString(new TaskRequest(tituloTooLong, "desc", false));

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.titulo").exists());
    }

    @Test
    void accessProtectedEndpoint_withTamperedToken_returnsUnauthorized() throws Exception {
        String token = registerAndLogin("uma");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 5: Ejecutar el test y confirmar que falla (RED)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskControllerTest
```

Expected: FAIL — no compila (`Task`, `TaskController`, etc. aún no conectados entre sí).

- [ ] **Step 6: Implementar `TaskService`, `TaskServiceImpl` (cache-aware) y `TaskController`**

`TaskService.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.List;

public interface TaskService {

    TaskResponse create(TaskRequest request, User currentUser);

    List<TaskResponse> findAll(User currentUser);

    TaskResponse findById(Long id, User currentUser);

    TaskResponse update(Long id, TaskRequest request, User currentUser);

    void delete(Long id, User currentUser);
}
```

`TaskServiceImpl.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final CachedTaskLookup cachedTaskLookup;

    public TaskServiceImpl(TaskRepository taskRepository, CachedTaskLookup cachedTaskLookup) {
        this.taskRepository = taskRepository;
        this.cachedTaskLookup = cachedTaskLookup;
    }

    @Override
    @Transactional
    public TaskResponse create(TaskRequest request, User currentUser) {
        Task task = new Task(request.titulo(), request.descripcion(), request.completada(), currentUser);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> findAll(User currentUser) {
        List<Task> tasks = currentUser.getRole() == Role.ADMIN
                ? taskRepository.findAll()
                : taskRepository.findByUser(currentUser);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse findById(Long id, User currentUser) {
        return TaskResponse.from(getTaskForCurrentUser(id, currentUser));
    }

    @Override
    @Transactional
    @CacheEvict(value = "tasks", key = "#id")
    public TaskResponse update(Long id, TaskRequest request, User currentUser) {
        Task task = getTaskForCurrentUser(id, currentUser);
        task.setTitulo(request.titulo());
        task.setDescripcion(request.descripcion());
        task.setCompletada(request.completada());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Override
    @Transactional
    @CacheEvict(value = "tasks", key = "#id")
    public void delete(Long id, User currentUser) {
        Task task = getTaskForCurrentUser(id, currentUser);
        taskRepository.delete(task);
    }

    /**
     * Looks up the task through the cache (CachedTaskLookup — a hit or a miss is
     * transparent from here), then ALWAYS enforces the ownership/admin check on every
     * call. A cache hit never skips this: the cache only remembers "what is task #id",
     * never "who is allowed to see it" — that decision depends on who is asking right
     * now, so it must run on every call regardless of where the Task came from. Returns
     * the mutable entity for callers (update/delete) that need to modify or remove it.
     */
    private Task getTaskForCurrentUser(Long id, User currentUser) {
        Task task = cachedTaskLookup.findById(id);
        requireAccess(task, currentUser);
        return task;
    }

    private void requireAccess(Task task, User currentUser) {
        boolean isOwner = task.getUser().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new TaskAccessDeniedException(task.getId());
        }
    }
}
```

`@CacheEvict` en `update`/`delete` invalida la entrada de `CachedTaskLookup` para ese `id` después de escribir. `create` no necesita evict: no hay nada cacheado todavía para un id que acaba de nacer. Nota de corrección ya cerrada en el spec: `@CacheEvict` solo se dispara por defecto si el método termina **sin lanzar excepción** (`beforeInvocation = false`, el valor por defecto de Spring). Como `update`/`delete` llaman primero a `getTaskForCurrentUser` (que lanza `TaskAccessDeniedException`/`TaskNotFoundException` **antes** de tocar la base de datos si el acceso no es válido), un intento fallido de escritura nunca dispara la invalidación — coherente, porque no hubo ninguna escritura real que invalidar. No hace falta fijar `beforeInvocation` explícitamente. El Step 9 de este Task lo verifica con un test dedicado.

`TaskController.java`:
```java
package dev.springbootdocs.examples.tasks;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponse created = taskService.create(request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/tasks")
    public List<TaskResponse> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        return taskService.findAll(principal.getUser());
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse findById(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.findById(id, principal.getUser());
    }

    @PutMapping("/tasks/{id}")
    public TaskResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.update(id, request, principal.getUser());
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        taskService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 7: Añadir a `GlobalExceptionHandler` los handlers de `TaskNotFoundException`/`TaskAccessDeniedException`**

Editar `GlobalExceptionHandler.java` (Task 2) añadiendo, al final de la clase, antes del cierre `}`:

```java
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiError> handleTaskNotFound(TaskNotFoundException ex) {
        ApiError error = ApiError.of(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(TaskAccessDeniedException.class)
    public ResponseEntity<ApiError> handleTaskAccessDenied(TaskAccessDeniedException ex) {
        ApiError error = ApiError.of(HttpStatus.FORBIDDEN.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
```

Una tarea ajena para un `USER` devuelve **403 Forbidden**, nunca 404 — 404 se reserva para "esta tarea no existe para nadie". Este handler capta `TaskAccessDeniedException`, lanzada tanto en cache-miss como en cache-hit por `requireAccess` (Step 6) — el resultado es idéntico en ambos casos, precisamente el punto que verifica Step 9.

- [ ] **Step 8: Ejecutar `TaskControllerTest` y confirmar que pasa (GREEN)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskControllerTest
```

Expected: PASS — 14/14 tests.

- [ ] **Step 9: Escribir `CacheBehaviorTest` — verifica que la caché realmente funciona y que nunca salta la comprobación de ownership**

```java
package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies caching against the REAL CacheManager the test profile wires up
 * (ConcurrentMapCacheManager, via spring.cache.type: simple) — not a mock of the cache
 * abstraction. This is the same @Cacheable/@CacheEvict machinery that runs against
 * Redis in production; only the backend differs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CacheBehaviorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    private String registerAndLogin(String username) throws Exception {
        String password = "password123";
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Long createTask(String token, String titulo) throws Exception {
        String json = objectMapper.writeValueAsString(new TaskRequest(titulo, "desc", false));
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private Cache tasksCache() {
        Cache cache = cacheManager.getCache("tasks");
        assertThat(cache).isNotNull();
        return cache;
    }

    @Test
    void getTaskById_populatesCache() throws Exception {
        String token = registerAndLogin("cara");
        Long id = createTask(token, "Tarea de cara");
        assertThat(tasksCache().get(id)).isNull();

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(tasksCache().get(id)).isNotNull();
    }

    @Test
    void updateTask_evictsCacheEntry() throws Exception {
        String token = registerAndLogin("dan");
        Long id = createTask(token, "Original");
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token));
        assertThat(tasksCache().get(id)).isNotNull();

        String updateJson = objectMapper.writeValueAsString(new TaskRequest("Actualizada", "desc", true));
        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk());

        assertThat(tasksCache().get(id)).isNull();
    }

    @Test
    void deleteTask_evictsCacheEntry() throws Exception {
        String token = registerAndLogin("eve");
        Long id = createTask(token, "Para borrar");
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token));
        assertThat(tasksCache().get(id)).isNotNull();

        mockMvc.perform(delete("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(tasksCache().get(id)).isNull();
    }

    @Test
    void cacheHit_stillEnforcesOwnership_forDifferentUser() throws Exception {
        String ownerToken = registerAndLogin("fay");
        String otherToken = registerAndLogin("gus");
        Long id = createTask(ownerToken, "Tarea de fay");

        // primera lectura: cache miss, la sirve el dueño
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        assertThat(tasksCache().get(id)).isNotNull();

        // segunda lectura del MISMO id: acierto de caché, pero pedida por alguien sin permiso
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedUpdate_byNonOwner_doesNotEvictCache() throws Exception {
        String ownerToken = registerAndLogin("hank");
        String otherToken = registerAndLogin("iris");
        Long id = createTask(ownerToken, "Tarea de hank");
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + ownerToken));
        assertThat(tasksCache().get(id)).isNotNull();

        String updateJson = objectMapper.writeValueAsString(new TaskRequest("Hackeada", null, false));
        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());

        assertThat(tasksCache().get(id)).isNotNull();
    }
}
```

- [ ] **Step 10: Ejecutar `CacheBehaviorTest` y confirmar que pasa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=CacheBehaviorTest
```

Expected: PASS — 5/5 tests. Si `cacheHit_stillEnforcesOwnership_forDifferentUser` fallara devolviendo 200 en vez de 403, la causa más probable es que `requireAccess` se haya movido a un método que `@Cacheable` esté envolviendo directamente (cacheando la decisión de autorización junto con el dato) — confirma que el diseño de `CachedTaskLookup` (cachea solo el dato, nunca la decisión de acceso) sigue intacto.

- [ ] **Step 11: Ejecutar la suite completa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, 31 tests en total (3 `JwtServiceTest` + 7 `AuthControllerTest` + 1 `GlobalExceptionHandlerTest` + 14 `TaskControllerTest` + 5 `CacheBehaviorTest` + 1 `contextLoads`).

- [ ] **Step 12: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/04-cache-redis
git commit -m "feat: add cached task lookup with ownership enforcement on every cache hit"
```

---

### Task 4: Backend Redis — TTL explícito y serialización JSON

**Files:**
- Modify: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/CacheConfig.java`

**Interfaces:**
- Consumes: cache `"tasks"` con clave `#id` (Task 3) — este Task solo cambia CÓMO se serializa/expira esa caché en el perfil main (Postgres+Redis reales), no el nombre ni las claves.
- Produces: nada nuevo consumido por otros Tasks — esta configuración solo aplica quan el `CacheManager` activo es `RedisCacheManager` (perfil main). Bajo `spring.cache.type: simple` (test y `h2`) esta clase no tiene efecto: no existe `RedisCacheManager` al que aplicarle el customizer.

- [ ] **Step 1: Añadir el `RedisCacheManagerBuilderCustomizer` a `CacheConfig`**

Editar `CacheConfig.java` (Task 3) reemplazando su contenido completo por:

```java
package dev.springbootdocs.examples.tasks;

import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String ENTITY_BASE_PACKAGE = "dev.springbootdocs.examples.tasks.";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(ENTITY_BASE_PACKAGE)
                .build();
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
        return builder -> builder.cacheDefaults(cacheConfiguration);
    }
}
```

Tres decisiones que vale la pena entender, no solo copiar:

1. **`entryTtl(Duration.ofMinutes(10))`** — sin TTL explícito, las entradas de `RedisCacheManager` viven para siempre. Una fase de caché sin expiración es una lección incompleta: en producción, algo (aunque sea solo el paso del tiempo) tiene que forzar una relectura eventual desde la base de datos.
2. **`GenericJacksonJsonRedisSerializer` en vez de la serialización Java por defecto (`JdkSerializationRedisSerializer`)** — serializar a JSON es legible en `redis-cli`, no exige que `Task`/`User` implementen `Serializable`, y es portable entre versiones de la app (un cambio de campo en la clase Java no rompe binariamente entradas ya cacheadas, como sí podría pasar con serialización Java nativa).
3. **`enableDefaultTyping(typeValidator)` con un validador acotado al propio paquete** — `RedisCache` cachea valores como `Object` genérico: al leer de Redis, el serializador no sabe estáticamente si debe reconstruir un `Task` o cualquier otra cosa, así que necesita que el propio JSON lleve el nombre de la clase. `enableDefaultTyping` activa eso. La alternativa más simple, `enableUnsafeDefaultTyping()` (sin validador), acepta reconstruir **cualquier** clase del classpath a partir de ese campo de tipo — una fuente clásica de deserialización insegura si algún día Redis dejara de ser un almacén de confianza exclusiva de esta app. Acotar el validador a `dev.springbootdocs.examples.tasks.` cierra esa puerta sin perder la funcionalidad que necesitamos.

Dos gotchas de nombres a tener en cuenta al escribir esto (ya cubiertos por los imports de arriba, pero fáciles de teclear mal): existen dos clases distintas llamadas `RedisCacheConfiguration` (la de Spring Boot en `org.springframework.boot.autoconfigure.cache`, que no se usa aquí, y la de Spring Data Redis en `org.springframework.data.redis.cache`, que sí) — y `RedisCacheManagerBuilderCustomizer` vive en `org.springframework.boot.cache.autoconfigure` en Spring Boot 4.x, no en el paquete `org.springframework.boot.autoconfigure.cache` de versiones anteriores.

- [ ] **Step 2: Compilar y confirmar que la suite sigue en verde (esta configuración solo se activa con `RedisCacheManager`, así que bajo el perfil de test — `spring.cache.type: simple` — no debería cambiar ningún resultado)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, mismos 31/31 tests que al final de Task 3 — cero cambios de comportamiento bajo el perfil de test, lo cual confirma que `redisCacheManagerBuilderCustomizer` no interfiere cuando el `CacheManager` activo es el simple. Si la compilación falla porque alguna de las clases de este Step no existe con ese nombre/paquete exacto en la versión de Spring Data Redis realmente resuelta, ajustar a la API real del proyecto generado (mismo criterio que con `bootVersion` en Task 1) y documentar el cambio en el mensaje de commit.

- [ ] **Step 3: Documentar el pendiente real — esta configuración nunca se ha probado contra un Redis de verdad en este entorno**

Docker no está disponible (Global Constraints), así que ninguna verificación de este Task ha ejercitado `RedisCacheManager` contra un Redis real — solo se ha confirmado que compila y que no rompe el perfil de test (que usa `ConcurrentMapCacheManager`, sin serialización). En particular, dos cosas quedan sin verificar empíricamente y deben probarse manualmente con Docker disponible (Task 8 lo deja como pendiente explícito):

- Que `Task.user` (proxy lazy de Hibernate) serializa y deserializa correctamente a través de `GenericJacksonJsonRedisSerializer` sin filtrar propiedades del proxy — la anotación `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` de `Task` (Task 3, Step 1) es la mitigación preventiva, pero no hay forma de confirmarla sin Redis real.
- Que `enableDefaultTyping(typeValidator)` con el `BasicPolymorphicTypeValidator` acotado al paquete propio efectivamente permite reconstruir `Task` al leer de Redis (y no, por ejemplo, un `LinkedHashMap` genérico, que rompería `TaskResponse.from(task)` con un `ClassCastException`).

- [ ] **Step 4: Commit**

```bash
git add examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/CacheConfig.java
git commit -m "feat: configure RedisCacheManager with explicit TTL and JSON serialization"
```

---

### Task 5: TDD — Endpoint admin-only y README

**Files:**
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/UserSummary.java`
- Create: `examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks/AdminController.java`
- Test: `examples/04-cache-redis/src/test/java/dev/springbootdocs/examples/tasks/AdminControllerTest.java`
- Create: `examples/04-cache-redis/README.md`

**Interfaces:**
- Consumes: `@EnableMethodSecurity` de `SecurityConfig` (Task 2), admin seed (Task 1), `/auth/login` (Task 2).
- Produces: `GET /admin/users` — usado por Task 7 (docs) como referencia (reutilizado tal cual de Fase 3, no forma parte del foco pedagógico de caché).

- [ ] **Step 1: Escribir `AdminControllerTest` — RED**

```java
package dev.springbootdocs.examples.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String username) throws Exception {
        String password = "password123";
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin12345"))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void listUsers_asAdmin_returnsOk() throws Exception {
        registerAndLogin("walt");
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'walt')]").exists())
                .andExpect(jsonPath("$[?(@.username == 'admin')]").exists());
    }

    @Test
    void listUsers_doesNotExposePasswordHash() throws Exception {
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void listUsers_asRegularUser_returnsForbidden() throws Exception {
        String userToken = registerAndLogin("xena");

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Ejecutar los tests y confirmar que fallan (RED)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=AdminControllerTest
```

Expected: FAIL — no compila (`AdminController` no existe todavía).

- [ ] **Step 3: Implementar `UserSummary` y `AdminController`**

```java
package dev.springbootdocs.examples.tasks;

public record UserSummary(Long id, String username, String role) {
}
```

```java
package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserSummary> listUsers() {
        return userRepository.findAll().stream()
                .map(user -> new UserSummary(user.getId(), user.getUsername(), user.getRole().name()))
                .toList();
    }
}
```

- [ ] **Step 4: Ejecutar los tests y confirmar que pasan (GREEN)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=AdminControllerTest
```

Expected: PASS — 4/4 tests.

- [ ] **Step 5: Ejecutar la suite completa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, 35 tests en total (3 `JwtServiceTest` + 7 `AuthControllerTest` + 1 `GlobalExceptionHandlerTest` + 14 `TaskControllerTest` + 5 `CacheBehaviorTest` + 4 `AdminControllerTest` + 1 `contextLoads`).

- [ ] **Step 6: Escribir el `README.md` del ejemplo**

```markdown
# 04-cache-redis

API REST de gestión de tareas — ejemplo ejecutable de la Fase 4 (Caché/Redis) del sitio **Spring Boot desde cero**. Mismo dominio y mismo API que `03-security-jwt` (auth JWT, roles, ownership) — el endpoint público es idéntico, cachear no le añade ni le quita ninguna ruta. La diferencia vive por debajo: las lecturas por id pasan primero por una caché (Redis en producción, memoria en desarrollo/tests).

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

Con `docker compose up -d` corriendo:

```bash
redis-cli -n 0 keys "tasks::*"
redis-cli -n 0 get "tasks::1"
```

Tras un `GET /tasks/1`, debería aparecer una clave `tasks::1` con el JSON de la tarea. Tras un `PUT`/`DELETE` sobre esa misma tarea, la clave desaparece.

## Tests

```bash
./mvnw test
```

Usan H2 en memoria + `spring.cache.type: simple` (ver `src/test/resources/application.yml`) — no requieren Docker, Postgres ni Redis. Los tests obtienen JWT reales registrando/logueando usuarios contra el propio API, sin autenticación simulada, y verifican la caché contra el `CacheManager` real de Spring, no contra un mock.
```

- [ ] **Step 7: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/04-cache-redis
git commit -m "feat: add admin-only endpoint and README to examples/04-cache-redis"
```

---

### Task 6: Añadir `04-cache-redis` a la matriz de CI

**Files:**
- Modify: `.github/workflows/examples-ci.yml`

**Interfaces:**
- Consumes: `examples/04-cache-redis/mvnw` funcional (Task 1), con tests de Tasks 2-5 que no necesitan Docker ni Redis (confirmado en Task 3/Task 4).

- [ ] **Step 1: Añadir la nueva entrada a `matrix.example`**

El bloque actual es:
```yaml
    strategy:
      matrix:
        example:
          - 00-hello-world
          - 01-fundamentos
          - 02-persistencia
          - 03-security-jwt
```

Cambiar a:
```yaml
    strategy:
      matrix:
        example:
          - 00-hello-world
          - 01-fundamentos
          - 02-persistencia
          - 03-security-jwt
          - 04-cache-redis
```

No modificar nada más del archivo.

- [ ] **Step 2: Validar sintaxis YAML localmente**

```bash
npx -y js-yaml .github/workflows/examples-ci.yml
```

Expected: imprime el YAML parseado sin errores.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/examples-ci.yml
git commit -m "ci: add 04-cache-redis to examples CI matrix"
```

---

### Task 7: Contenido de la Fase 4 (índice + 4 páginas)

**Files:**
- Modify: `docs-site/docs/04-cache-redis/index.md`
- Create: `docs-site/docs/04-cache-redis/spring-cache-basico.md`
- Create: `docs-site/docs/04-cache-redis/redis-como-backend.md`
- Create: `docs-site/docs/04-cache-redis/invalidacion-de-cache.md`
- Create: `docs-site/docs/04-cache-redis/probar-con-cache-real.md`

**Interfaces:**
- Consumes: código real de `examples/04-cache-redis` (Tasks 1-6) — los snippets deben coincidir con ese código.
- Produces: rutas `/docs/04-cache-redis/spring-cache-basico`, `.../redis-como-backend`, `.../invalidacion-de-cache`, `.../probar-con-cache-real` — verificadas en Task 8.

- [ ] **Step 1: Reescribir `index.md`**

```markdown
---
title: Caché y Redis
---

# Caché y Redis

Hasta ahora, cada `GET /tasks/{id}` de las Fases 1-3 volvía a consultar la base de datos, aunque fuera la misma tarea pedida diez veces seguidas. Esta fase añade una caché delante de esa lectura: la primera vez que alguien pide una tarea, la respuesta se guarda; las siguientes veces, mientras nada la haya invalidado, se sirve sin tocar Postgres. El API público no cambia ni una ruta — cachear es una capa transparente por debajo, no una funcionalidad nueva de cara al cliente.

## Contenido

1. [Spring Cache básico](./spring-cache-basico)
2. [Redis como backend](./redis-como-backend)
3. [Invalidación de caché](./invalidacion-de-cache)
4. [Probar con caché real](./probar-con-cache-real)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/04-cache-redis`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/04-cache-redis): la misma API de tareas de la Fase 3 (autenticación JWT, roles, ownership), ahora con caché sobre las lecturas.

```bash
docker compose up -d
cd examples/04-cache-redis
./mvnw spring-boot:run
```
```

- [ ] **Step 2: Crear `spring-cache-basico.md`**

```markdown
---
title: Spring Cache básico
sidebar_position: 1
---

# Spring Cache básico

Spring Cache es una **abstracción**: un conjunto de anotaciones (`@Cacheable`, `@CacheEvict`) que declaran "este método se puede cachear" sin que el código de negocio sepa nada sobre dónde vive esa caché. Por debajo puede haber un `Map` en memoria, Redis, Caffeine o cualquier otro backend — el código anotado es el mismo en los tres casos. Esta página cubre la parte que no depende del backend; [Redis como backend](./redis-como-backend) cubre la que sí.

## Activar la abstracción

```java
@Configuration
@EnableCaching
public class CacheConfig {
}
```

`@EnableCaching` no es opcional ni implícito por tener `spring-boot-starter-cache` en el `pom.xml` — a diferencia de otras auto-configuraciones de Spring Boot, esta es opt-in deliberado. Sin esta anotación, `@Cacheable`/`@CacheEvict` se ignoran silenciosamente: no hay ningún error, simplemente nunca se cachea nada.

## `@Cacheable`

```java
@Component
public class CachedTaskLookup {

    private final TaskRepository taskRepository;

    public CachedTaskLookup(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Cacheable(value = "tasks", key = "#id")
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
```

`value = "tasks"` es el nombre de la caché (puede haber varias, cada una con su propia configuración); `key = "#id"` dice qué parte de los argumentos identifica la entrada — aquí, el id de la tarea. La primera llamada con un `id` dado ejecuta el método normalmente y guarda el resultado; las siguientes llamadas con el mismo `id` devuelven el valor guardado sin ejecutar el cuerpo del método — ni la consulta a `taskRepository` se dispara. Si el método lanza una excepción (por ejemplo `TaskNotFoundException`), no se cachea nada: solo se guardan retornos normales.

## La trampa de la auto-invocación

`CachedTaskLookup` es una clase propia, separada de `TaskServiceImpl` — no un método privado de `TaskServiceImpl` que se llame a sí mismo. La razón no es de estilo: es necesaria para que `@Cacheable` funcione.

Spring implementa `@Cacheable` con un **proxy AOP**: envuelve el bean real en un objeto intermediario que intercepta las llamadas externas y decide si ejecuta el método real o devuelve el valor cacheado. Ese proxy solo entra en juego cuando la llamada llega **desde fuera** del bean, a través de la referencia que Spring inyectó. Una llamada `this.metodo()` hecha desde dentro de la propia clase nunca pasa por el proxy — la JVM la resuelve directamente sobre `this`, sin que Spring tenga oportunidad de interceptarla.

Si `findById` fuera un método privado de `TaskServiceImpl` en vez de vivir en `CachedTaskLookup`, y otro método de esa misma clase lo llamara como `this.findById(id)`, la anotación `@Cacheable` se ignoraría por completo — sin ningún error en consola, sin ninguna excepción, simplemente sin cachear nunca. Es uno de los bugs de Spring Cache más difíciles de detectar precisamente porque no falla de forma ruidosa.

```java
@Service
public class TaskServiceImpl implements TaskService {

    private final CachedTaskLookup cachedTaskLookup; // bean DISTINTO

    // ...

    private Task getTaskForCurrentUser(Long id, User currentUser) {
        Task task = cachedTaskLookup.findById(id); // llamada externa: SÍ pasa por el proxy
        requireAccess(task, currentUser);
        return task;
    }
}
```

`TaskServiceImpl` inyecta `CachedTaskLookup` como cualquier otra dependencia y lo llama desde fuera — esa llamada sí atraviesa el proxy de `CachedTaskLookup`, así que `@Cacheable` funciona de verdad.
```

- [ ] **Step 3: Crear `redis-como-backend.md`**

```markdown
---
title: Redis como backend
sidebar_position: 2
---

# Redis como backend

`@Cacheable`/`@CacheEvict` (ver [Spring Cache básico](./spring-cache-basico)) no cambian según el backend — lo que cambia es la configuración del `CacheManager` que Spring Boot autoconfigura. Con `spring-boot-starter-data-redis` en el classpath y sin forzar otro tipo, Spring Boot elige `RedisCacheManager` automáticamente.

## Redis en `docker-compose.yml`

```yaml
services:
  postgres:
    # ...
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

Sin volumen: Redis aquí es una caché, no una fuente de verdad — perder su contenido en un reinicio del contenedor es aceptable, y de hecho coherente con lo que representa.

## TTL explícito

```java
RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10));
```

Sin `entryTtl`, las entradas de `RedisCacheManager` viven para siempre. Una fase de caché sin expiración estaría incompleta: en producción, algo tiene que forzar eventualmente una relectura desde la base de datos, aunque solo sea el paso del tiempo — sobre todo porque, como se ve en [Invalidación de caché](./invalidacion-de-cache), la invalidación explícita (`@CacheEvict`) solo cubre las escrituras que pasan por *esta* aplicación.

## Por qué JSON y no serialización Java nativa

Por defecto, si no se configura ningún serializador, Spring Data Redis usa `JdkSerializationRedisSerializer` — la serialización binaria estándar de Java, que exige que las clases cacheadas implementen `Serializable` y produce un formato binario opaco. En su lugar, esta fase usa un serializador JSON:

```java
GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(typeValidator)
        .build();

RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10))
        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
```

JSON es legible directamente con `redis-cli` (útil para depurar), no exige `Serializable` en las entidades, y es más tolerante a cambios de versión de la app que un formato binario ligado a la implementación exacta de la clase Java.

## Un detalle propio de Jackson 3

`GenericJacksonJsonRedisSerializer` cachea valores como `Object` genérico — al leer de Redis, no sabe estáticamente si debe reconstruir un `Task`, un `User` o cualquier otra cosa, así que necesita que el propio JSON lleve el nombre de la clase (`"default typing"`). A diferencia de su predecesor de Jackson 2 (`GenericJackson2JsonRedisSerializer`, ya deprecado), la versión de Jackson 3 **no** activa eso por defecto — hay que pedirlo explícitamente, y con un validador que acote qué clases se pueden reconstruir así:

```java
PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("dev.springbootdocs.examples.tasks.")
        .build();
```

La alternativa más corta, `enableUnsafeDefaultTyping()` (sin validador), acepta reconstruir *cualquier* clase del classpath a partir de ese campo de tipo — el nombre "unsafe" no es decorativo: es la puerta clásica de una deserialización insegura si Redis dejara de ser, algún día, un almacén exclusivo de esta app. Acotar el validador al propio paquete evita ese riesgo sin perder la funcionalidad que hace falta aquí.

## Un gotcha de nombres, no de diseño

Dos parejas de clases con el mismo nombre simple conviven en el classpath de este proyecto y es fácil importar la equivocada:

- `RedisCacheConfiguration` existe tanto en `org.springframework.boot.autoconfigure.cache` (de Spring Boot, no se usa aquí) como en `org.springframework.data.redis.cache` (de Spring Data Redis, la correcta para `entryTtl`/`serializeValuesWith`).
- `RedisCacheManagerBuilderCustomizer` vive en `org.springframework.boot.cache.autoconfigure` en Spring Boot 4.x — un paquete distinto al de versiones anteriores de Spring Boot.

Ninguno de los dos es un problema de diseño: es puramente que el IDE puede autocompletar el import equivocado si no se presta atención.
```

- [ ] **Step 4: Crear `invalidacion-de-cache.md`**

```markdown
---
title: Invalidación de caché
sidebar_position: 3
---

# Invalidación de caché

Una caché sin invalidación es un bug esperando a pasar: en cuanto el dato subyacente cambia, cualquier lectura cacheada queda desactualizada hasta que expire por TTL (ver [Redis como backend](./redis-como-backend)). Esta fase invalida explícitamente en cada escritura, en vez de depender solo del tiempo.

## `@CacheEvict` en las escrituras

```java
@Override
@Transactional
@CacheEvict(value = "tasks", key = "#id")
public TaskResponse update(Long id, TaskRequest request, User currentUser) {
    Task task = getTaskForCurrentUser(id, currentUser);
    task.setTitulo(request.titulo());
    task.setDescripcion(request.descripcion());
    task.setCompletada(request.completada());
    return TaskResponse.from(taskRepository.save(task));
}

@Override
@Transactional
@CacheEvict(value = "tasks", key = "#id")
public void delete(Long id, User currentUser) {
    Task task = getTaskForCurrentUser(id, currentUser);
    taskRepository.delete(task);
}
```

Mismo `value`/`key` que el `@Cacheable` de `CachedTaskLookup` (ver [Spring Cache básico](./spring-cache-basico)) — así invalidan exactamente la entrada que ese id tenía cacheada. `create` no lleva `@CacheEvict`: no hay nada cacheado todavía para un id que acaba de nacer.

## Por qué el orden de las comprobaciones importa

`@CacheEvict` solo se dispara, por defecto, si el método anotado termina **sin lanzar excepción** — es el valor por defecto de su atributo `beforeInvocation` (`false`). `update`/`delete` llaman primero a `getTaskForCurrentUser`, que comprueba ownership y lanza `TaskAccessDeniedException`/`TaskNotFoundException` **antes** de tocar la base de datos si el acceso no es válido:

```java
private Task getTaskForCurrentUser(Long id, User currentUser) {
    Task task = cachedTaskLookup.findById(id);
    requireAccess(task, currentUser);
    return task;
}
```

Un intento de `update`/`delete` que falla por falta de permiso nunca llega a invalidar la caché — y es el comportamiento correcto: no hubo ninguna escritura real que invalidar. No hace falta fijar `beforeInvocation` a mano; el valor por defecto de Spring ya es el que se necesita aquí, siempre que la comprobación de acceso ocurra antes de la escritura (no después).

## La comprobación de ownership nunca se salta, ni en un acierto de caché

Esto es lo más importante de toda la fase, y por eso tiene su propio test dedicado. La caché memoriza **qué es la tarea con id X**, nunca **quién puede verla** — esa segunda pregunta depende de quién está preguntando en este momento, así que tiene que evaluarse en cada llamada, sin excepción:

```java
private Task getTaskForCurrentUser(Long id, User currentUser) {
    Task task = cachedTaskLookup.findById(id); // puede venir de caché o de la BD — es indistinguible desde aquí
    requireAccess(task, currentUser);          // esto SIEMPRE se ejecuta, venga de donde venga el dato
    return task;
}
```

La clave está en qué se anotó con `@Cacheable`: solo `CachedTaskLookup.findById(Long id)`, que no recibe ni conoce al usuario actual. Si en cambio se hubiera anotado un método que tomara `(id, currentUser)` y devolviera directamente la `Task` ya autorizada, un segundo usuario sin permiso podría beneficiarse de la decisión de acceso tomada para el primero — la caché estaría memorizando una autorización, no solo un dato. Separar "traer el dato" (cacheable) de "autorizar el acceso" (siempre se ejecuta) es la decisión de diseño que evita esa fuga.
```

- [ ] **Step 5: Crear `probar-con-cache-real.md`**

```markdown
---
title: Probar con caché real
sidebar_position: 4
---

# Probar con caché real

## Sin Docker: `spring.cache.type: simple`

```yaml
spring:
  cache:
    type: simple
```

Fuerza `ConcurrentMapCacheManager`, el `CacheManager` que Spring Boot autoconfigura cuando no hay (o no se quiere usar) un backend externo: un `Map` en memoria por caché, sin serialización de por medio. Es lo que usan tanto el perfil de test (`src/test/resources/application.yml`) como el perfil `h2` (`application-h2.yml`) — mismas anotaciones `@Cacheable`/`@CacheEvict` de producción, funcionando contra una caché real, no un mock, sin necesitar Redis.

## Confirmar la caché contra el `CacheManager` real, no contra un mock

```java
@Autowired
private CacheManager cacheManager;

private Cache tasksCache() {
    Cache cache = cacheManager.getCache("tasks");
    assertThat(cache).isNotNull();
    return cache;
}

@Test
void getTaskById_populatesCache() throws Exception {
    String token = registerAndLogin("cara");
    Long id = createTask(token, "Tarea de cara");
    assertThat(tasksCache().get(id)).isNull();

    mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

    assertThat(tasksCache().get(id)).isNotNull();
}
```

Inyectar el `CacheManager` y preguntarle directamente por la entrada (`cacheManager.getCache("tasks").get(id)`) prueba el comportamiento real de la abstracción de caché — nada de esto pasaría si `@Cacheable` estuviera silenciosamente desactivado por la trampa de auto-invocación descrita en [Spring Cache básico](./spring-cache-basico); el test fallaría de inmediato porque la entrada nunca aparecería.

El mismo enfoque confirma la invalidación:

```java
@Test
void updateTask_evictsCacheEntry() throws Exception {
    // ... crear la tarea, leerla (puebla la caché) ...
    assertThat(tasksCache().get(id)).isNotNull();

    // ... PUT /tasks/{id} ...

    assertThat(tasksCache().get(id)).isNull();
}
```

Y, el test más importante de esta fase, que un acierto de caché nunca se sirve a quien no tiene permiso (ver [Invalidación de caché](./invalidacion-de-cache)):

```java
@Test
void cacheHit_stillEnforcesOwnership_forDifferentUser() throws Exception {
    // primera lectura del dueño: cache miss
    mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk());

    // segunda lectura del MISMO id: acierto de caché, pero de alguien sin permiso
    mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden());
}
```

## Verificación manual contra Redis real

Con Docker disponible y `docker compose up -d` corriendo:

```bash
redis-cli -n 0 keys "tasks::*"
redis-cli -n 0 get "tasks::1"
```

Tras un `GET /tasks/1`, debería aparecer una clave `tasks::1` con el JSON de la tarea (gracias a `GenericJacksonJsonRedisSerializer`, ver [Redis como backend](./redis-como-backend)); tras un `PUT`/`DELETE` sobre esa misma tarea, la clave desaparece. Este entorno no tiene Docker disponible, así que esta verificación —junto con confirmar que `Task.user` (una relación lazy de Hibernate) serializa correctamente hacia JSON real— queda documentada como pendiente explícito para quien continúe este proyecto con Docker instalado.
```

- [ ] **Step 6: Verificar el build del sitio**

```bash
cd docs-site
npm run build
```

Expected: `[SUCCESS] Generated static files in "build".` — sin enlaces rotos.

- [ ] **Step 7: Commit**

```bash
cd /d/Proyectos/spring-boot
git add docs-site/docs/04-cache-redis
git commit -m "docs: add Fase 4 content (Spring Cache, Redis, invalidation, testing)"
```

---

### Task 8: Verificación final de la Fase 4

**Files:** ninguno nuevo — solo verificación.

- [ ] **Step 1: Build limpio del sitio**

```bash
cd docs-site
rm -rf build .docusaurus
npm run build
```

Expected: éxito, cero enlaces rotos.

- [ ] **Step 2: Tests limpios del ejemplo (H2 + caché simple, sin Docker)**

```bash
cd examples/04-cache-redis
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B clean verify
```

Expected: `BUILD SUCCESS`, 35/35 tests.

- [ ] **Step 3: Confirmar el bit de ejecución de `mvnw`**

```bash
git ls-files -s examples/04-cache-redis/mvnw
```

Expected: modo `100755`.

- [ ] **Step 4: Validar `docker-compose.yml` de nuevo (sintaxis, no ejecución real — Docker no disponible)**

```bash
npx -y js-yaml examples/04-cache-redis/docker-compose.yml
```

Expected: parsea sin errores, dos servicios (`postgres`, `redis`).

- [ ] **Step 5: Verificar las 5 rutas nuevas y el orden del sidebar**

```bash
cd docs-site
npm run serve &
sleep 3
for path in 04-cache-redis 04-cache-redis/spring-cache-basico 04-cache-redis/redis-como-backend 04-cache-redis/invalidacion-de-cache 04-cache-redis/probar-con-cache-real; do
  curl -s -o /dev/null -w "%{http_code} /docs/$path\n" "http://localhost:3000/spring-boot-docs/docs/$path"
done
kill %1
```

Expected: `200` para las 5 rutas. Comprobar en el HTML servido o en `build/` que las 4 páginas aparecen en el sidebar en el orden: Spring Cache básico → Redis como backend → Invalidación de caché → Probar con caché real.

- [ ] **Step 6: Confirmar CI matrix actualizada**

```bash
cd /d/Proyectos/spring-boot
grep -A5 "matrix:" .github/workflows/examples-ci.yml
```

Expected: la lista incluye `00-hello-world`, `01-fundamentos`, `02-persistencia`, `03-security-jwt` y `04-cache-redis`.

- [ ] **Step 7: Confirmar que ningún acierto de caché salta la comprobación de ownership**

Ya cubierto por `CacheBehaviorTest` (Task 3: `cacheHit_stillEnforcesOwnership_forDifferentUser`, `failedUpdate_byNonOwner_doesNotEvictCache`) — confirmar que ambos tests siguen en verde en el Step 2 de esta tarea. No repetir manualmente.

- [ ] **Step 8: Documentar los pendientes reales (Docker/Redis/Postgres) — no simularlos**

Confirmar en el reporte de esta tarea, explícitamente, que:
- Ninguna verificación de este plan ha levantado Postgres ni Redis reales, ni ha ejecutado `docker compose up` contra un daemon Docker de verdad (no disponible en este entorno).
- `RedisCacheManager` (Task 4) nunca se ha ejercitado contra un Redis real — solo se confirmó que compila y que no interfiere con el perfil de test. En particular, la serialización de `Task.user` (relación lazy de Hibernate) vía `GenericJacksonJsonRedisSerializer`, y la reconstrucción correcta de `Task` (no un `Map` genérico) al leer de Redis gracias a `enableDefaultTyping`, quedan sin confirmar empíricamente.
- La única garantía real sobre las migraciones Flyway (incluida la semilla del admin) viene de que se ejecutan también contra H2 en los tests — no es lo mismo que confirmarlas contra Postgres real.
- Esto queda como pendiente explícito para un humano con Docker instalado, igual que en las Fases 0-3.

- [ ] **Step 9: Commit final si queda algo pendiente**

```bash
git status
git add -A
git commit -m "chore: Fase 4 complete — Cache/Redis content + Spring Cache/Redis example + CI" --allow-empty
```

---

## Self-Review

**Cobertura del spec:** estructura de contenido (índice + 4 páginas, Task 7), ejemplo ejecutable independiente reutilizando auth/roles de Fase 3 (Task 2), diseño de caché cerrado en el spec — `CachedTaskLookup` como bean separado para evitar la trampa de auto-invocación, `@Cacheable`/`@CacheEvict` con las claves correctas, ownership siempre verificado (Task 3) —, `RedisCacheManager` con TTL de 10 min y serialización JSON vía `GenericJacksonJsonRedisSerializer` (Task 4, con la investigación de la API real de Jackson 3/Spring Data Redis 4.x documentada en Global Constraints), `docker-compose.yml` con Postgres (5435) y Redis (Task 1), perfil de test con `spring.cache.type: simple` + H2 y perfil `h2` sin Docker (Task 1), test dedicado contra el `CacheManager` real que confirma hit tras lectura y miss tras update/delete (Task 3, `CacheBehaviorTest`), test de que un acierto de caché nunca salta ownership (Task 3), endpoint admin-only reutilizado (Task 5), matriz de CI (Task 6), criterios de aceptación — build sin broken links, `mvn verify` con H2 sin Docker/Redis, sidebar en orden, `mvnw` con bit de ejecución correcto, `pom.xml` limpio, `docker-compose.yml` con dos servicios sintácticamente válido, `findAll` deliberadamente no cacheado (fuera de alcance del spec, y en efecto `TaskServiceImpl.findAll` de Task 3 no lleva `@Cacheable`) — todos cubiertos. Las lecciones técnicas heredadas de Fases 0-3, listadas explícitamente en el spec, aplicadas: sin `bootVersion` fijo (Task 1 Step 1), bit de `mvnw` verificado antes del commit (Task 1 Step 4), `pom.xml` limpiado como paso propio (Task 1 Step 5), `examples/04-cache-redis` completamente independiente (ningún task toca `03-security-jwt`), `ddl-auto: validate` en los tres perfiles (Task 1 Steps 6-8), verificación de que `GenericJacksonJsonRedisSerializer` funciona con Jackson 3 investigada en vivo en vez de asumida (Global Constraints, con fuentes de la documentación oficial de migración de Spring Data Redis), perfil `h2` documentado y parte del entregable desde el diseño (Task 1 Step 7, Task 5 README).

**Placeholders:** ninguno de contenido/código. `bootVersion` deliberadamente omitido. `TU_USUARIO` es el mismo placeholder ya rastreado desde la Fase 0. El `<HASH>` de `V3__seed_admin_user.sql` (Task 1 Step 10) no es un placeholder sin resolver — dado el mismo tratamiento que en Fase 3 (el paso da el código exacto para generarlo). El riesgo real y explícitamente no verificable en este entorno (serialización de `Task.user` contra Redis real, sin Docker) se documenta como pendiente en Task 4 Step 3 y Task 8 Step 8 — no se simula ni se da por buena una suposición.

**Consistencia de tipos/nombres:** `TaskService`/`TaskServiceImpl` con la firma `(TaskRequest, User)` idéntica en `TaskController` (Task 3) y en las páginas de docs (Task 7); `CachedTaskLookup.findById(Long)` con la misma firma en `TaskServiceImpl` (Task 3), `CacheBehaviorTest` (vía `cacheManager.getCache("tasks").get(id)`, Task 3) y `spring-cache-basico.md`/`invalidacion-de-cache.md` (Task 7); nombre de caché `"tasks"` y clave `#id` consistentes entre `@Cacheable` (Task 3), `@CacheEvict` (Task 3), `RedisCacheManagerBuilderCustomizer` (Task 4, aplica a cualquier caché del `CacheManager` sin necesitar el nombre explícito) y los ejemplos de `redis-cli` en `probar-con-cache-real.md` (Task 7, `tasks::1`); `JwtService.generateToken(String, Role)`/`isValid(String)`/`extractUsername(String)` con la misma firma en `JwtServiceTest`, `AuthController` y `JwtAuthenticationFilter` (todos Task 2); `TaskResponse`/`UserSummary` con los mismos campos en su definición (Tasks 3/5) y en los ejemplos de docs; rutas `/auth/register`, `/auth/login`, `/tasks`, `/tasks/{id}`, `/admin/users` consistentes entre controllers, tests y docs; credenciales del admin seed (`admin`/`admin12345`) idénticas en `V3__seed_admin_user.sql` (Task 1), todos los `*ControllerTest`/`CacheBehaviorTest` (Tasks 2-5), `README.md` (Task 5) y `probar-con-cache-real.md` (Task 7); puertos consistentes (Postgres `5435`, Redis `6379` por defecto) en `docker-compose.yml`, `application.yml` y `README.md` (Task 1/5); `JAVA_HOME=/c/jdk-23.0.1` usado consistentemente en todas las invocaciones de `mvnw`; conteo acumulado de tests correcto en cada "Expected" (12 tras Task 2, 31 tras Task 3, 35 tras Task 5, 35/35 en la verificación final de Task 8).
