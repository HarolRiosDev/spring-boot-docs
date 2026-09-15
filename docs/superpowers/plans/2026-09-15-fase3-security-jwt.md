# Fase 3 — Security/JWT — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir a la Fase 2 su continuación natural — Spring Security con JWT propio, roles (`USER`/`ADMIN`) y autorización por dueño sobre el API de tareas — junto con un nuevo ejemplo ejecutable (`examples/03-security-jwt`) y 4 páginas de contenido nuevas.

**Architecture:** Mismo patrón que las Fases 0-2: contenido Docusaurus en `docs-site/docs/03-security-jwt/` (categoría ya existente) más un proyecto Maven/Spring Boot independiente en `examples/03-security-jwt/`, añadido a la matriz de `examples-ci.yml`. El ejemplo reintroduce el API de tareas de la Fase 2 casi sin cambios de forma y le añade encima: registro/login con JWT propio (firmado con `jjwt`, sin proveedor externo), un filtro (`OncePerRequestFilter`) que valida el token en cada petición, y autorización con dos mecanismos distintos y complementarios — ownership a nivel de servicio (`USER` solo ve/edita sus tareas, `ADMIN` todas) y `@PreAuthorize` declarativo sobre un endpoint admin-only (`GET /admin/users`).

**Tech Stack:** Spring Boot (versión estable actual de `start.spring.io`, sin fijar un número), Java 21, Maven Wrapper, Spring Security, `io.jsonwebtoken` (jjwt) 0.12.6, Spring Data JPA, PostgreSQL, Flyway, H2 (solo para tests), MockMvc.

**Spec:** [2026-09-15-fase3-security-jwt-design.md](../specs/2026-09-15-fase3-security-jwt-design.md)

## Global Constraints

- Idioma del contenido: **español**; código e identificadores en inglés/sin acentos.
- `examples/03-security-jwt` es un proyecto Maven **independiente y autocontenido** — no modifica `examples/02-persistencia`.
- **No fijar la versión de Spring Boot.** Omitir `bootVersion` en la llamada a Initializr.
- **El bit de ejecución de `mvnw` debe verificarse explícitamente antes de cada commit** (`git update-index --chmod=+x mvnw` + `git ls-files -s` mostrando `100755`).
- **Limpiar el `pom.xml` generado por Initializr** de boilerplate vacío (`<description/>`, `<url/>`, `<licenses>`, `<developers>`, `<scm>`) como paso explícito de Task 1.
- Nombres: groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks` (mismo paquete que Fases 1-2), artifactId `tasks-security`, carpeta `examples/03-security-jwt`, clase principal `TasksSecurityApplication` (de `name=TasksSecurity`).
- `spring.jpa.hibernate.ddl-auto: validate` en ambos perfiles (main y test) — el esquema lo gestiona exclusivamente Flyway.
- Puerto de Postgres en el host: `5434` (Fase 2 ya usa `5433`; evita choque si ambos ejemplos se levantan a la vez).
- **Docker no está disponible en este entorno** (`docker --version` → comando no encontrado, confirmado en Fase 2). Ninguna tarea de este plan puede verificar `docker compose up` contra Postgres real — la verificación de `docker-compose.yml` se limita a validar su sintaxis YAML. Queda como pendiente explícito para el usuario (Task 7 lo documenta).
- Máquina de desarrollo: usar `JAVA_HOME=/c/jdk-23.0.1` (bash) para toda invocación de `./mvnw`.
- `io.jsonwebtoken` (jjwt) no está en el BOM de Spring Boot — versión fijada explícitamente. Verificada en Maven Central el día de este plan: **0.12.6** (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`). Si al implementar existe una versión más reciente, usar esa en su lugar (mismo espíritu que no fijar `bootVersion`, aplicado a una dependencia que si necesita un número).
- Tests de integración usan **JWT reales obtenidos vía `/auth/register` + `/auth/login`** — nunca `@WithMockUser` ni autenticación simulada por el framework de test.
- Fuera de alcance: refresh tokens, logout/blacklist, OAuth2/OIDC, recuperación de contraseña, verificación de email, rate limiting, CORS, Testcontainers (Fase 6).

---

## File Structure

```
docs-site/docs/03-security-jwt/
├── _category_.json                     # ya existe, no tocar
├── index.md                            # reescrito
├── spring-security-basico.md           # nuevo
├── autenticacion-jwt.md                # nuevo
├── roles-y-autorizacion.md             # nuevo
└── probar-endpoints-protegidos.md      # nuevo

examples/03-security-jwt/               # nuevo proyecto Maven (vía Spring Initializr)
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/...
├── docker-compose.yml
├── src/main/java/dev/springbootdocs/examples/tasks/
│   ├── TasksSecurityApplication.java   # generado por Initializr
│   ├── Role.java                       # enum USER/ADMIN
│   ├── User.java                       # @Entity
│   ├── UserRepository.java
│   ├── UserPrincipal.java              # UserDetails que envuelve User
│   ├── UserDetailsServiceImpl.java
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── AuthResponse.java
│   ├── UsernameAlreadyExistsException.java
│   ├── AuthController.java
│   ├── JwtService.java
│   ├── JwtAuthenticationFilter.java
│   ├── SecurityConfig.java
│   ├── CustomAuthenticationEntryPoint.java
│   ├── CustomAccessDeniedHandler.java
│   ├── ApiError.java
│   ├── ValidationApiError.java
│   ├── GlobalExceptionHandler.java
│   ├── Task.java                       # @Entity, ahora con User owner
│   ├── TaskRequest.java
│   ├── TaskResponse.java               # DTO de salida (sin exponer User)
│   ├── TaskNotFoundException.java
│   ├── TaskAccessDeniedException.java
│   ├── TaskRepository.java
│   ├── TaskService.java
│   ├── TaskServiceImpl.java
│   ├── TaskController.java
│   ├── UserSummary.java                # DTO de salida (sin exponer password)
│   └── AdminController.java
├── src/main/resources/
│   ├── application.yml                 # datasource Postgres real, JWT secret, ddl-auto: validate
│   └── db/migration/
│       ├── V1__create_users_table.sql
│       ├── V2__create_tasks_table.sql
│       └── V3__seed_admin_user.sql
├── src/test/resources/
│   └── application.yml                 # datasource H2, ddl-auto: validate
├── src/test/java/dev/springbootdocs/examples/tasks/
│   ├── TasksSecurityApplicationTests.java  # generado por Initializr
│   ├── JwtServiceTest.java
│   ├── AuthControllerTest.java
│   ├── TaskControllerTest.java
│   └── AdminControllerTest.java
└── README.md

.github/workflows/examples-ci.yml       # modificado: +1 línea en matrix.example
```

---

### Task 1: Bootstrap de `examples/03-security-jwt`

**Files:**
- Create: `examples/03-security-jwt/` (scaffold vía Spring Initializr)
- Modify: `examples/03-security-jwt/pom.xml` (limpieza + dependencias jjwt)
- Create: `examples/03-security-jwt/docker-compose.yml`
- Create: `examples/03-security-jwt/src/main/resources/db/migration/V1__create_users_table.sql`
- Create: `examples/03-security-jwt/src/main/resources/db/migration/V2__create_tasks_table.sql`
- Create: `examples/03-security-jwt/src/main/resources/db/migration/V3__seed_admin_user.sql`
- Create: `examples/03-security-jwt/src/main/resources/application.yml`
- Create: `examples/03-security-jwt/src/test/resources/application.yml`
- Delete: `examples/03-security-jwt/src/main/resources/application.properties`

**Interfaces:**
- Produces: proyecto Maven con wrapper funcional, esquema `users`+`tasks` creado por Flyway (incluido el usuario `admin` semilla), listo para que Task 2 añada las clases Java.

- [ ] **Step 1: Generar el proyecto base desde Spring Initializr (sin fijar `bootVersion`)**

```bash
mkdir -p /d/Proyectos/spring-boot/examples/03-security-jwt
cd /d/Proyectos/spring-boot/examples
curl https://start.spring.io/starter.zip \
  -d dependencies=web,validation,data-jpa,postgresql,flyway,h2,security \
  -d type=maven-project \
  -d language=java \
  -d javaVersion=21 \
  -d groupId=dev.springbootdocs.examples \
  -d artifactId=tasks-security \
  -d name=TasksSecurity \
  -d packageName=dev.springbootdocs.examples.tasks \
  -o tasks-security.zip
```

- [ ] **Step 2: Descomprimir en la carpeta final y limpiar el zip**

```bash
cd 03-security-jwt
unzip -o ../tasks-security.zip
rm ../tasks-security.zip
```

- [ ] **Step 3: Verificar que el wrapper funciona**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -v
```

Expected: imprime versión de Apache Maven y un JDK 21+ sin errores. Si el `pom.xml` generado usa nombres de dependencia distintos a los esperados (por ejemplo `spring-boot-starter-webmvc` en vez de `-web`), no es un error — Task 2 debe adaptarse a la realidad del proyecto generado, no asumir que sigue igual que en la Fase 2.

- [ ] **Step 4: Corregir el bit de ejecución de `mvnw` — antes de cualquier otro paso**

```bash
cd /d/Proyectos/spring-boot
git add examples/03-security-jwt
git update-index --chmod=+x examples/03-security-jwt/mvnw
git ls-files -s examples/03-security-jwt/mvnw
```

Expected: la última línea muestra el modo `100755`. Si muestra `100644`, repetir `git update-index --chmod=+x` hasta confirmarlo.

- [ ] **Step 5: Limpiar el `pom.xml` de boilerplate vacío de Initializr y añadir las dependencias de jjwt**

Abrir `examples/03-security-jwt/pom.xml`. Eliminar `<url/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>`, el `<scm>` vacío, y sustituir `<description/>` por:

```xml
<description>API REST de gestión de tareas protegida con Spring Security y JWT propio.</description>
```

Añadir la propiedad de versión de jjwt dentro de `<properties>` (junto a `<java.version>`):

```xml
<jjwt.version>0.12.6</jjwt.version>
```

Añadir estas tres dependencias dentro de `<dependencies>`, junto a las generadas por Initializr:

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

`jjwt-api` en scope normal (el código del proyecto usa sus tipos, `Jwts`/`Claims`); `jjwt-impl`/`jjwt-jackson` solo en `runtime` (patrón estándar de jjwt: el código nunca referencia estos dos directamente).

No tocar `groupId`, `artifactId`, `version`, `name`, `parent`, `properties.java.version` ni el resto de `dependencies`/`build`.

- [ ] **Step 6: Convertir `application.properties` a `application.yml` (perfil principal, Postgres real)**

```bash
rm src/main/resources/application.properties
```

Crear `src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: 03-security-jwt
  datasource:
    url: jdbc:postgresql://localhost:5434/tasks_security
    username: tasks_security
    password: tasks_security
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate

app:
  jwt:
    secret: "local-dev-secret-please-change-in-production-0123456789abcdef"
    expiration-millis: 3600000
```

- [ ] **Step 7: Crear el perfil de test (H2, sin Docker)**

Crear `src/test/resources/application.yml`:
```yaml
spring:
  application:
    name: 03-security-jwt
  datasource:
    url: jdbc:h2:mem:tasks_security;MODE=PostgreSQL
    username: sa
    password:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate

app:
  jwt:
    secret: "test-secret-key-for-integration-tests-only-0123456789abcdef"
    expiration-millis: 3600000
```

- [ ] **Step 8: Crear las migraciones de `users` y `tasks`**

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

- [ ] **Step 9: Generar el hash BCrypt del admin seed y crear `V3__seed_admin_user.sql`**

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

Ejecutarlo apoyándose en el plugin de Spring Boot ya presente en el `pom.xml` (evita añadir un plugin nuevo solo para esto):

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

Nota: BCrypt genera una sal aleatoria en cada ejecución — el hash exacto no es reproducible ni importa que lo sea, solo que `BCryptPasswordEncoder.matches("admin12345", hash)` sea `true`, lo cual se confirma indirectamente en Task 2 (`AuthControllerTest`, login como admin) y explícitamente en Task 4 (`AdminControllerTest`).

- [ ] **Step 10: Crear `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: tasks_security
      POSTGRES_USER: tasks_security
      POSTGRES_PASSWORD: tasks_security
    ports:
      - "5434:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

- [ ] **Step 11: Validar la sintaxis YAML de `docker-compose.yml`**

```bash
npx -y js-yaml docker-compose.yml
```

Expected: imprime el YAML parseado sin errores. (Docker no está disponible en este entorno — ver Global Constraints.)

- [ ] **Step 12: Confirmar que el test de contexto por defecto pasa usando H2 — ejercita las 3 migraciones reales**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`. El único test en este punto es `TasksSecurityApplicationTests.contextLoads`, pero Flyway ya aplica `V1`, `V2` y `V3` contra H2 en modo PostgreSQL — antes de que exista ninguna entidad JPA. Si falla por el `INSERT` de `V3` (por ejemplo, un hash mal copiado con saltos de línea), revisar el contenido exacto del archivo.

- [ ] **Step 13: Commit**

```bash
git add examples/03-security-jwt
git commit -m "chore: bootstrap examples/03-security-jwt (users/tasks schema, Postgres, Flyway, H2 tests)"
```

---

### Task 2: TDD — Registro, login y validación de JWT

**Files:**
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/Role.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/User.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/UserRepository.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/UserPrincipal.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/UserDetailsServiceImpl.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/RegisterRequest.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/LoginRequest.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/AuthResponse.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/UsernameAlreadyExistsException.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/AuthController.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/JwtService.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/JwtAuthenticationFilter.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/SecurityConfig.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/CustomAuthenticationEntryPoint.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/CustomAccessDeniedHandler.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/ApiError.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/ValidationApiError.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/GlobalExceptionHandler.java`
- Test: `examples/03-security-jwt/src/test/java/dev/springbootdocs/examples/tasks/JwtServiceTest.java`
- Test: `examples/03-security-jwt/src/test/java/dev/springbootdocs/examples/tasks/AuthControllerTest.java`

**Interfaces:**
- Consumes: proyecto de Task 1 (esquema `users`/`tasks`, admin seed).
- Produces: `POST /auth/register`, `POST /auth/login` (usados por Task 3 y Task 4 para obtener tokens reales en sus tests). `SecurityConfig` ya tiene `@EnableMethodSecurity` activo (lo usará Task 4). `JwtService.generateToken(String username, Role role)`, `JwtService.isValid(String token)`, `JwtService.extractUsername(String token)` — únicas señales públicas que Task 3 necesita del filtro (no necesita tocarlo). `UserPrincipal.getUser()` devuelve el `User` completo — es lo que Task 3 usará vía `@AuthenticationPrincipal` para conocer al dueño de una tarea.

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

Nota: como en Fase 2, `User` es una `@Entity` desde el primer momento, así que el arranque del contexto ya valida contra el esquema de `V1__create_users_table.sql` (`ddl-auto: validate`).

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

`JwtServiceTest.java` (test unitario puro, sin `@SpringBootTest` — es rápido y no necesita base de datos):

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

Nota: el claim `"role"` viaja en el token (útil para que un cliente lea el rol sin llamar a otro endpoint) pero **no** es la fuente de autorización — `JwtAuthenticationFilter` (Step 7) recarga el usuario desde la base de datos en cada petición, así que un cambio de rol en `users` surte efecto de inmediato, sin esperar a que expire el token. Esto se explica explícitamente en el contenido de Task 6.

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

- [ ] **Step 8: Crear `CustomAuthenticationEntryPoint` y `CustomAccessDeniedHandler`**

Primero, `ApiError.java` y `ValidationApiError.java` (idénticos a los de Fases 1-2):

```java
package dev.springbootdocs.examples.tasks;

import java.time.Instant;

public record ApiError(int status, String message, String timestamp) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Instant.now().toString());
    }
}
```

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

`CustomAccessDeniedHandler` gestiona los `AccessDeniedException` de Spring Security (los que lanza `@PreAuthorize` — ver Task 4). Es un componente distinto de `GlobalExceptionHandler`: este último es un `@RestControllerAdvice` de Spring MVC, aquel vive en la cadena de filtros de seguridad — no compiten porque manejan tipos de excepción diferentes (`TaskAccessDeniedException`, propia de la Fase 3 y creada en Task 3, es un `RuntimeException` normal que sí gestiona `GlobalExceptionHandler`).

Import necesario: `tools.jackson.databind.ObjectMapper` (Jackson 3, no `com.fasterxml.jackson`, lección de fases anteriores). Si Initializr sirvió una versión de Spring Boot distinta, verificar el paquete real antes de asumir.

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

`AuthenticationManager` se construye a partir de `AuthenticationConfiguration`, que ensambla automáticamente un `DaoAuthenticationProvider` usando el único `UserDetailsService` (`UserDetailsServiceImpl`) y el único `PasswordEncoder` (`passwordEncoder()` de arriba) presentes en el contexto — no hace falta declarar el `DaoAuthenticationProvider` a mano. `@EnableMethodSecurity` habilita `@PreAuthorize`, que no se usa todavía en este Task pero sí en Task 4.

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

`authenticationManager.authenticate(...)` delega en el `DaoAuthenticationProvider` (`UserDetailsServiceImpl` + `PasswordEncoder`, ensamblados en `SecurityConfig`): si el username no existe o la contraseña no coincide, lanza `BadCredentialsException` — con el mismo mensaje genérico en ambos casos (comportamiento por defecto de Spring Security), así que la respuesta nunca revela si el username existía.

- [ ] **Step 14: Implementar `GlobalExceptionHandler`**

```java
package dev.springbootdocs.examples.tasks;

import java.util.HashMap;
import java.util.Map;
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

- [ ] **Step 15: Ejecutar el test y confirmar que pasa (GREEN)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=AuthControllerTest
```

Expected: PASS — 7/7 tests.

- [ ] **Step 16: Ejecutar la suite completa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, 11 tests en total (3 `JwtServiceTest` + 7 `AuthControllerTest` + 1 `contextLoads`).

- [ ] **Step 17: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/03-security-jwt
git commit -m "feat: add registration, login and JWT validation to examples/03-security-jwt"
```

---

### Task 3: TDD — API de tareas protegido, con dueño

**Files:**
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/Task.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/TaskRequest.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/TaskResponse.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/TaskNotFoundException.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/TaskAccessDeniedException.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/TaskRepository.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/TaskService.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/TaskServiceImpl.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/TaskController.java`
- Modify: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/GlobalExceptionHandler.java`
- Test: `examples/03-security-jwt/src/test/java/dev/springbootdocs/examples/tasks/TaskControllerTest.java`

**Interfaces:**
- Consumes: `POST /auth/register` + `POST /auth/login` de Task 2 (para obtener tokens reales en el test); `UserPrincipal.getUser()` de Task 2.
- Produces: `POST /tasks`, `GET /tasks`, `GET/PUT/DELETE /tasks/{id}` — usados por Task 6 (docs) como referencia. `TaskResponse` es la forma pública de una tarea (sin exponer `User`) — Task 4 no la necesita, pero cualquier página de docs que muestre una respuesta JSON debe usar esta forma.

- [ ] **Step 1: Escribir `TaskControllerTest` — RED**

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
    void accessProtectedEndpoint_withTamperedToken_returnsUnauthorized() throws Exception {
        String token = registerAndLogin("uma");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Ejecutar los tests y confirmar que fallan (RED)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskControllerTest
```

Expected: FAIL — no compila (`Task`, `TaskRequest`, `TaskController` no existen todavía).

- [ ] **Step 3: Implementar `Task`, `TaskRequest`, `TaskResponse`, excepciones**

`Task.java`:
```java
package dev.springbootdocs.examples.tasks;

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

`TaskRequest.java` (idéntico a Fases 1-2 — el dueño nunca llega en el body, se asigna server-side):
```java
package dev.springbootdocs.examples.tasks;

import jakarta.validation.constraints.NotBlank;

public record TaskRequest(@NotBlank String titulo, String descripcion, boolean completada) {
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

Nota didáctica para el contenido (Task 6): `Task` nunca se serializa directamente en una respuesta — expondría `user.password` (el hash) vía Jackson. `TaskResponse` es el límite explícito entre la entidad JPA y lo que ve el cliente.

- [ ] **Step 4: Implementar `TaskRepository`, `TaskService`, `TaskServiceImpl`**

```java
package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUser(User user);
}
```

```java
package dev.springbootdocs.examples.tasks;

import java.util.List;

public interface TaskService {

    Task create(TaskRequest request, User currentUser);

    List<Task> findAll(User currentUser);

    Task findById(Long id, User currentUser);

    Task update(Long id, TaskRequest request, User currentUser);

    void delete(Long id, User currentUser);
}
```

```java
package dev.springbootdocs.examples.tasks;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public Task create(TaskRequest request, User currentUser) {
        Task task = new Task(request.titulo(), request.descripcion(), request.completada(), currentUser);
        return taskRepository.save(task);
    }

    @Override
    public List<Task> findAll(User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return taskRepository.findAll();
        }
        return taskRepository.findByUser(currentUser);
    }

    @Override
    public Task findById(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        requireAccess(task, currentUser);
        return task;
    }

    @Override
    @Transactional
    public Task update(Long id, TaskRequest request, User currentUser) {
        Task task = findById(id, currentUser);
        task.setTitulo(request.titulo());
        task.setDescripcion(request.descripcion());
        task.setCompletada(request.completada());
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public void delete(Long id, User currentUser) {
        Task task = findById(id, currentUser);
        taskRepository.delete(task);
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

`findById` centraliza la comprobación de ownership — `update` y `delete` la heredan gratis al llamarlo primero, en vez de duplicar la lógica de `requireAccess` en cada método.

- [ ] **Step 5: Implementar `TaskController`**

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
        Task created = taskService.create(request, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(created));
    }

    @GetMapping("/tasks")
    public List<TaskResponse> findAll(@AuthenticationPrincipal UserPrincipal principal) {
        return taskService.findAll(principal.getUser()).stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/tasks/{id}")
    public TaskResponse findById(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return TaskResponse.from(taskService.findById(id, principal.getUser()));
    }

    @PutMapping("/tasks/{id}")
    public TaskResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return TaskResponse.from(taskService.update(id, request, principal.getUser()));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        taskService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 6: Añadir a `GlobalExceptionHandler` los handlers de `TaskNotFoundException` y `TaskAccessDeniedException`**

Editar el archivo de Task 2 (no reescribirlo entero), añadiendo estos dos métodos a la clase existente:

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

- [ ] **Step 7: Ejecutar los tests y confirmar que pasan (GREEN)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskControllerTest
```

Expected: PASS — 13/13 tests.

- [ ] **Step 8: Ejecutar la suite completa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, 24 tests en total (3 `JwtServiceTest` + 7 `AuthControllerTest` + 13 `TaskControllerTest` + 1 `contextLoads`).

- [ ] **Step 9: Commit**

```bash
git add examples/03-security-jwt
git commit -m "feat: add ownership-based task API to examples/03-security-jwt"
```

---

### Task 4: TDD — Endpoint admin-only y README

**Files:**
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/UserSummary.java`
- Create: `examples/03-security-jwt/src/main/java/dev/springbootdocs/examples/tasks/AdminController.java`
- Test: `examples/03-security-jwt/src/test/java/dev/springbootdocs/examples/tasks/AdminControllerTest.java`
- Create: `examples/03-security-jwt/README.md`

**Interfaces:**
- Consumes: `@EnableMethodSecurity` de `SecurityConfig` (Task 2), admin seed (Task 1), `/auth/login` (Task 2).
- Produces: `GET /admin/users` — usado por Task 6 (docs) como ejemplo de `@PreAuthorize`.

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

`@PreAuthorize("hasRole('ADMIN')")` funciona porque `UserPrincipal.getAuthorities()` (Task 2) expone `"ROLE_ADMIN"`/`"ROLE_USER"` — `hasRole('ADMIN')` añade el prefijo `ROLE_` automáticamente al comparar.

- [ ] **Step 4: Ejecutar los tests y confirmar que pasan (GREEN)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=AdminControllerTest
```

Expected: PASS — 4/4 tests.

- [ ] **Step 5: Ejecutar la suite completa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, 28 tests en total (3 `JwtServiceTest` + 7 `AuthControllerTest` + 13 `TaskControllerTest` + 4 `AdminControllerTest` + 1 `contextLoads`).

- [ ] **Step 6: Escribir el `README.md` del ejemplo**

```markdown
# 03-security-jwt

API REST de gestión de tareas — ejemplo ejecutable de la Fase 3 (Security/JWT) del sitio **Spring Boot desde cero**. Mismo dominio que `01-fundamentos`/`02-persistencia`, ahora protegido con autenticación JWT propia, roles y ownership de tareas.

## Requisitos

- JDK 21 o superior.
- Docker (para levantar Postgres). Los tests NO necesitan Docker — usan H2 en memoria automáticamente.

## Levantar la base de datos

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5434` (usuario/contraseña/base de datos: `tasks_security`).

## Ejecutar

```bash
./mvnw spring-boot:run
```

Flyway crea el esquema y siembra un usuario administrador al arrancar:

- **username:** `admin`
- **password:** `admin12345`

## Endpoints

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/auth/register` | pública | Registrar un usuario nuevo (rol `USER`) |
| POST | `/auth/login` | pública | Login, devuelve un JWT |
| POST | `/tasks` | JWT | Crear una tarea (propia) |
| GET | `/tasks` | JWT | Listar tareas (propias si `USER`, todas si `ADMIN`) |
| GET | `/tasks/{id}` | JWT | Obtener una tarea (propia, o cualquiera si `ADMIN`) |
| PUT | `/tasks/{id}` | JWT | Actualizar una tarea (propia, o cualquiera si `ADMIN`) |
| DELETE | `/tasks/{id}` | JWT | Eliminar una tarea (propia, o cualquiera si `ADMIN`) |
| GET | `/admin/users` | JWT + rol `ADMIN` | Listar todos los usuarios |

Las rutas protegidas requieren el header `Authorization: Bearer <token>` obtenido de `/auth/login`.

## Tests

```bash
./mvnw test
```

Usan H2 en memoria (ver `src/test/resources/application.yml`) — no requieren Docker ni Postgres. Los tests obtienen JWT reales registrando/logueando usuarios contra el propio API, sin autenticación simulada.
```

- [ ] **Step 7: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/03-security-jwt
git commit -m "feat: add admin-only endpoint and README to examples/03-security-jwt"
```

---

### Task 5: Añadir `03-security-jwt` a la matriz de CI

**Files:**
- Modify: `.github/workflows/examples-ci.yml`

**Interfaces:**
- Consumes: `examples/03-security-jwt/mvnw` funcional (Task 1), con tests de Tasks 2-4 que no necesitan Docker.

- [ ] **Step 1: Añadir la nueva entrada a `matrix.example`**

El bloque actual es:
```yaml
    strategy:
      matrix:
        example:
          - 00-hello-world
          - 01-fundamentos
          - 02-persistencia
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
git commit -m "ci: add 03-security-jwt to examples CI matrix"
```

---

### Task 6: Contenido de la Fase 3 (índice + 4 páginas)

**Files:**
- Modify: `docs-site/docs/03-security-jwt/index.md`
- Create: `docs-site/docs/03-security-jwt/spring-security-basico.md`
- Create: `docs-site/docs/03-security-jwt/autenticacion-jwt.md`
- Create: `docs-site/docs/03-security-jwt/roles-y-autorizacion.md`
- Create: `docs-site/docs/03-security-jwt/probar-endpoints-protegidos.md`

**Interfaces:**
- Consumes: código real de `examples/03-security-jwt` (Tasks 1-4) — los snippets deben coincidir con ese código.
- Produces: rutas `/docs/03-security-jwt/spring-security-basico`, `.../autenticacion-jwt`, `.../roles-y-autorizacion`, `.../probar-endpoints-protegidos` — verificadas en Task 7.

- [ ] **Step 1: Reescribir `index.md`**

```markdown
---
title: Security/JWT
---

# Security/JWT

Hasta ahora, cualquiera podía llamar al API de tareas de las Fases 1-2 sin identificarse. Esta fase añade autenticación real: cada usuario se registra, inicia sesión y recibe un JWT propio (emitido por la propia app, sin depender de un proveedor externo como Keycloak o Auth0) que debe enviar en cada petición. Además, cada tarea pasa a tener un dueño, y aparece un segundo rol (`ADMIN`) con permisos ampliados.

## Contenido

1. [Spring Security básico](./spring-security-basico)
2. [Autenticación con JWT](./autenticacion-jwt)
3. [Roles y autorización](./roles-y-autorizacion)
4. [Probar endpoints protegidos](./probar-endpoints-protegidos)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/03-security-jwt`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/03-security-jwt): la misma API de tareas de la Fase 2, ahora protegida.

```bash
docker compose up -d
cd examples/03-security-jwt
./mvnw spring-boot:run
```
```

- [ ] **Step 2: Crear `spring-security-basico.md`**

```markdown
---
title: Spring Security básico
sidebar_position: 1
---

# Spring Security básico

En cuanto añades `spring-boot-starter-security` al `pom.xml`, tu aplicación cambia de comportamiento sin que escribas una sola línea de configuración: **todos los endpoints pasan a requerir autenticación**, y Spring Boot genera un usuario `user` con una contraseña aleatoria que imprime en los logs al arrancar. Es una demostración de "seguro por defecto" — el punto de partida es bloquearlo todo, y tú decides explícitamente qué abrir.

## `SecurityFilterChain`

En vez de la contraseña generada, definimos nuestra propia configuración:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
```

- `csrf().disable()` — la protección CSRF existe para clientes con sesión y cookies (un navegador). Nuestro API es *stateless* (sin sesión, cada petición lleva su propio JWT), así que CSRF no aplica.
- `sessionCreationPolicy(STATELESS)` — Spring Security no crea ni usa `HttpSession`. Cada petición se autentica desde cero a partir del token, no de un estado guardado en el servidor.
- `authorizeHttpRequests` — `/auth/**` (registro y login) es público; todo lo demás requiere estar autenticado.
- `@EnableMethodSecurity` — habilita `@PreAuthorize` sobre métodos de controller, usado más adelante en [Roles y autorización](./roles-y-autorizacion).

## `PasswordEncoder`

Nunca se guarda una contraseña en texto plano. `BCryptPasswordEncoder` aplica un hash de un solo sentido (no se puede "deshacer") con una sal aleatoria incorporada, por lo que dos usuarios con la misma contraseña obtienen hashes distintos:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Se usa al registrar (`passwordEncoder.encode(...)`, ver [Autenticación con JWT](./autenticacion-jwt)) y, indirectamente, al hacer login: Spring Security compara la contraseña recibida contra el hash guardado con `passwordEncoder.matches(...)`, nunca comparando strings directamente.

## `UserDetailsService` respaldado por una entidad real

Spring Security necesita saber cómo cargar un usuario y sus roles. Se lo decimos implementando `UserDetailsService`:

```java
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

`UserPrincipal` es una clase propia que implementa `UserDetails` envolviendo nuestra entidad `User` — así el resto de Spring Security (y nuestros propios controllers, vía `@AuthenticationPrincipal`) puede acceder tanto a lo que Spring Security necesita (`getUsername()`, `getPassword()`, `getAuthorities()`) como al `User` completo con su `id`, que hace falta para comprobar quién es el dueño de una tarea.

Con `UserDetailsServiceImpl` y `passwordEncoder()` como los únicos beans de ese tipo en el contexto, Spring Security ensambla automáticamente un `AuthenticationManager` capaz de validar credenciales contra la base de datos — sin que tengamos que conectarlos a mano.
```

- [ ] **Step 3: Crear `autenticacion-jwt.md`**

```markdown
---
title: Autenticación con JWT
sidebar_position: 2
---

# Autenticación con JWT

Un **JWT** (JSON Web Token) es un token firmado que contiene información (*claims*) sobre quién es el usuario. A diferencia de una sesión de servidor, el propio token lleva la prueba de identidad — el servidor no necesita guardar nada para verificarlo, solo comprobar la firma. Esta fase usa un JWT **propio**: lo emite y lo valida la misma aplicación, sin depender de un proveedor externo (Keycloak, Auth0...).

## Registro

```java
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
```

Todo usuario nuevo nace con rol `USER` — no existe una forma de auto-asignarse `ADMIN` vía el API (el único `ADMIN` inicial lo siembra una migración de Flyway, ver [Probar endpoints protegidos](./probar-endpoints-protegidos)). La contraseña nunca se guarda tal cual: pasa por `passwordEncoder.encode(...)` antes de llegar a la base de datos.

## Login

```java
@PostMapping("/auth/login")
public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password()));
    User user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new IllegalStateException("Usuario autenticado pero no encontrado"));
    String token = jwtService.generateToken(user.getUsername(), user.getRole());
    return new AuthResponse(token);
}
```

`authenticationManager.authenticate(...)` es el mismo mecanismo que Spring Security monta automáticamente a partir de `UserDetailsServiceImpl` y `PasswordEncoder` (ver [Spring Security básico](./spring-security-basico)): busca el usuario, compara el hash, y si algo no cuadra lanza `BadCredentialsException` — con el mismo mensaje genérico tanto si el usuario no existe como si la contraseña es incorrecta, para no revelar qué usernames están registrados.

## Firmar el token

```java
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
    // ...
}
```

Usamos la librería [`jjwt`](https://github.com/jwtk/jjwt) para construir y firmar el token con una clave HMAC (simétrica: la misma clave firma y verifica, guardada en `application.yml` como `app.jwt.secret`, nunca en el código). El `subject` es el username; el claim `"role"` viaja también en el token, pero es solo informativo — como verás en [Roles y autorización](./roles-y-autorizacion), la autorización real no confía en ese claim.

## Validar el token en cada petición

Un filtro propio se ejecuta antes que el resto de Spring Security en cada petición:

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                if (jwtService.isValid(token)) {
                    String username = jwtService.extractUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (UsernameNotFoundException ignored) {
                // token válido pero usuario ya no existe: se trata como no autenticado
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

`OncePerRequestFilter` garantiza que el filtro corre exactamente una vez por petición. Si el header `Authorization` trae un `Bearer <token>` válido, el filtro rellena el `SecurityContextHolder` — de ahí en adelante, para el resto de la petición (controllers incluidos), Spring Security actúa como si el usuario se hubiera autenticado de la forma tradicional. Si no hay token, o es inválido, simplemente no se rellena nada y la petición sigue: será `authorizeHttpRequests` (o `@PreAuthorize`) quien la rechace más adelante si el endpoint requería autenticación.

Registrado en `SecurityConfig` con `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)` — se ejecuta antes que el filtro estándar de usuario/contraseña de Spring Security, que en nuestro caso no llega a usarse para peticiones normales (solo lo usa `authenticationManager.authenticate(...)` en `/auth/login`).
```

- [ ] **Step 4: Crear `roles-y-autorizacion.md`**

```markdown
---
title: Roles y autorización
sidebar_position: 3
---

# Roles y autorización

Esta fase combina dos mecanismos de autorización distintos, cada uno donde encaja mejor.

## Ownership: ¿de quién es esta tarea?

Cada `Task` tiene un dueño (`user_id`). Decidir si la petición actual puede acceder a una tarea concreta requiere primero cargarla y compararla contra quién hace la petición — eso no es un simple "¿tiene este rol?", así que vive como código explícito en `TaskServiceImpl`, no en una anotación:

```java
@Override
public Task findById(Long id, User currentUser) {
    Task task = taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
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
```

Una tarea ajena para un `USER` devuelve **403 Forbidden**, nunca 404 — 404 se reserva para "esta tarea no existe para nadie". Mezclarlos filtraría información: un 404 en vez de un 403 le diría a un atacante que probó IDs al azar cuáles existen y cuáles no.

`update` y `delete` reutilizan `findById` como primer paso, así que heredan la misma comprobación sin repetirla.

## `@PreAuthorize`: autorización declarativa por rol

Para un caso más simple — "solo un `ADMIN` puede llamar a este endpoint, sin más matices" — una anotación es más clara que código a mano:

```java
@GetMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public List<UserSummary> listUsers() {
    return userRepository.findAll().stream()
            .map(user -> new UserSummary(user.getId(), user.getUsername(), user.getRole().name()))
            .toList();
}
```

`hasRole('ADMIN')` compara contra las autoridades del usuario autenticado (`ROLE_ADMIN`, que añade `UserPrincipal.getAuthorities()` — el prefijo `ROLE_` lo añade automáticamente `hasRole`, no hace falta escribirlo). Si un `USER` normal llama a este endpoint, Spring Security lanza `AccessDeniedException` **antes** de que se ejecute el cuerpo del método — nunca llega a `userRepository.findAll()`.

Nota el porqué de elegir uno u otro: `/admin/users` es "todo o nada" según el rol, así que `@PreAuthorize` es la herramienta correcta. `/tasks/{id}` depende de datos (¿es tuya o no?) que no existen todavía cuando se evalúa la anotación — por eso vive en el servicio.

## Por qué el rol se recarga desde la base de datos, no del JWT

El JWT lleva un claim `"role"` (ver [Autenticación con JWT](./autenticacion-jwt)), pero `JwtAuthenticationFilter` no lo usa para decidir permisos — vuelve a cargar el `User` completo con `UserDetailsServiceImpl` en cada petición. Es más trabajo (una consulta a la base de datos por petición) a cambio de una garantía real: si cambias el rol de un usuario en la base de datos, el cambio se aplica en su siguiente petición, sin esperar a que expire un token que ya tenía el rol antiguo grabado.

## Respuestas de error consistentes

Por defecto, Spring Security responde a un fallo de autenticación/autorización con una redirección o una página HTML — pensado para un navegador, no para un cliente de API. Dos componentes propios lo sustituyen por el mismo formato `ApiError` que ya usa `GlobalExceptionHandler`:

```java
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    // 401 — sin token, token inválido o expirado
}

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    // 403 — autenticado, pero @PreAuthorize rechaza la petición
}
```

Se registran en `SecurityConfig` con `.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(...).accessDeniedHandler(...))`. El 403 de una tarea ajena, en cambio, no pasa por aquí — lo lanza `TaskAccessDeniedException` desde el servicio, y lo captura `GlobalExceptionHandler` como cualquier otra excepción de negocio.
```

- [ ] **Step 5: Crear `probar-endpoints-protegidos.md`**

```markdown
---
title: Probar endpoints protegidos
sidebar_position: 4
---

# Probar endpoints protegidos

Con todo conectado, así es como se usa el API de principio a fin.

## Levantar la base de datos y la aplicación

```bash
docker compose up -d
cd examples/03-security-jwt
./mvnw spring-boot:run
```

Al arrancar, Flyway crea `users` y `tasks`, y siembra un usuario `admin` (contraseña `admin12345`) — ver `V3__seed_admin_user.sql`. Ese hash se generó una sola vez, offline, con `BCryptPasswordEncoder`; nunca se guarda una contraseña en texto plano ni siquiera en la migración.

## Registrar un usuario y obtener un token

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "ana", "password": "password123"}'
```

Responde `201 Created` con `{ "token": "eyJhbGciOi..." }`. Guarda ese valor:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "ana", "password": "password123"}' | jq -r .token)
```

## Usar el token

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"titulo": "Comprar leche", "descripcion": "2 litros", "completada": false}'

curl http://localhost:8080/tasks -H "Authorization: Bearer $TOKEN"
```

Sin el header `Authorization`, ambas peticiones devuelven `401`. Si intentas leer una tarea de otro usuario con tu token, obtienes `403` — pero como `admin`, la misma petición funciona contra cualquier tarea.

## Cómo prueban esto los tests, sin `@WithMockUser`

Los tests de `TaskControllerTest` no simulan la autenticación con anotaciones de test — obtienen un JWT real llamando a `/auth/register` y `/auth/login`, exactamente como haría un cliente real:

```java
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
```

La ventaja: si algo se rompe en el filtro JWT, en `SecurityConfig`, o en cómo se firma/valida el token, estos tests lo detectan — una autenticación simulada por el framework de test nunca pasa por ese código.

## Sin Docker

`./mvnw test` **no necesita Docker ni Postgres**. Igual que en la Fase 2, `src/test/resources/application.yml` apunta a H2 en modo compatibilidad PostgreSQL, y las mismas migraciones de Flyway (incluida la semilla del admin) se aplican también ahí — así que los tests de `AdminControllerTest` (login como `admin`/`admin12345`) comprueban de verdad que la migración `V3` sembró un hash válido, no uno que solo "parece" correcto.
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
git add docs-site/docs/03-security-jwt
git commit -m "docs: add Fase 3 content (Spring Security, JWT, roles, testing)"
```

---

### Task 7: Verificación final de la Fase 3

**Files:** ninguno nuevo — solo verificación.

- [ ] **Step 1: Build limpio del sitio**

```bash
cd docs-site
rm -rf build .docusaurus
npm run build
```

Expected: éxito, cero enlaces rotos.

- [ ] **Step 2: Tests limpios del ejemplo (H2, sin Docker)**

```bash
cd examples/03-security-jwt
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B clean verify
```

Expected: `BUILD SUCCESS`, 28/28 tests.

- [ ] **Step 3: Confirmar el bit de ejecución de `mvnw`**

```bash
git ls-files -s examples/03-security-jwt/mvnw
```

Expected: modo `100755`.

- [ ] **Step 4: Validar `docker-compose.yml` de nuevo (sintaxis, no ejecución real — Docker no disponible)**

```bash
npx -y js-yaml examples/03-security-jwt/docker-compose.yml
```

Expected: parsea sin errores.

- [ ] **Step 5: Verificar las 5 rutas nuevas y el orden del sidebar**

```bash
cd docs-site
npm run serve &
sleep 3
for path in 03-security-jwt 03-security-jwt/spring-security-basico 03-security-jwt/autenticacion-jwt 03-security-jwt/roles-y-autorizacion 03-security-jwt/probar-endpoints-protegidos; do
  curl -s -o /dev/null -w "%{http_code} /docs/$path\n" "http://localhost:3000/spring-boot-docs/docs/$path"
done
kill %1
```

Expected: `200` para las 5 rutas. Comprobar en el HTML servido o en `build/` que las 4 páginas aparecen en el sidebar en el orden: Spring Security básico → Autenticación con JWT → Roles y autorización → Probar endpoints protegidos.

- [ ] **Step 6: Confirmar CI matrix actualizada**

```bash
cd /d/Proyectos/spring-boot
grep -A4 "matrix:" .github/workflows/examples-ci.yml
```

Expected: la lista incluye `00-hello-world`, `01-fundamentos`, `02-persistencia` y `03-security-jwt`.

- [ ] **Step 7: Confirmar que ningún endpoint de `/tasks` es accesible sin token, y que una tarea ajena da 403 no 404**

Ya cubierto por `TaskControllerTest` (Task 3: `createTask_withoutToken_returnsUnauthorized`, `getTaskById_whenNotOwnerAndNotAdmin_returnsForbidden`) — confirmar que ambos tests siguen en verde en el Step 2 de esta tarea. No repetir manualmente.

- [ ] **Step 8: Documentar el pendiente real (Docker/Postgres) — no simularlo**

Confirmar en el reporte de esta tarea, explícitamente, que:
- Ninguna verificación de este plan ha levantado Postgres real ni ha ejecutado `docker compose up` contra un daemon Docker de verdad (no disponible en este entorno).
- La única garantía real sobre las migraciones Flyway (incluida la semilla del admin) viene de que se ejecutan también contra H2 en los tests — no es lo mismo que confirmarlas contra Postgres real.
- Esto queda como pendiente explícito para un humano con Docker instalado, igual que en las Fases 0-2.

- [ ] **Step 9: Commit final si queda algo pendiente**

```bash
git status
git add -A
git commit -m "chore: Fase 3 complete — Security/JWT content + Spring Security/JWT example + CI" --allow-empty
```

---

## Self-Review

**Cobertura del spec:** estructura de contenido (índice + 4 páginas, Task 6), ejemplo ejecutable con registro/login/JWT (Task 2), API de tareas con ownership (Task 3), endpoint admin-only con `@PreAuthorize` (Task 4), integración en CI (Task 5), criterios de aceptación — build sin broken links, `mvn verify` con H2 sin Docker, sidebar en orden, `mvnw` con bit de ejecución correcto, `pom.xml` limpio, 403 (no 404) en tarea ajena (Task 7). Las lecciones técnicas heredadas de Fases 0-2 aplicadas explícitamente: sin `bootVersion` fijo (Task 1 Step 1), bit de `mvnw` verificado antes del commit (Task 1 Step 4), `pom.xml` limpiado como paso propio (Task 1 Step 5), `examples/03-security-jwt` completamente independiente (ningún task toca `02-persistencia`), `ddl-auto: validate` y H2 sin `@ActiveProfiles` en ambos perfiles (Task 1 Steps 6-7). Refinamientos añadidos durante la escritura del plan, no presentes literalmente en la spec pero coherentes con ella: `TaskResponse`/`UserSummary` como DTOs de salida para no filtrar el hash de contraseña vía Jackson (mencionado explícitamente como nota didáctica, Task 3 Step 3 y Task 4 Step 3); el claim `"role"` del JWT es informativo, no la fuente de autorización (Task 2 Step 5, explicado en Task 6 Step 4); versión de jjwt verificada en vivo contra Maven Central (0.12.6) en vez de asumida.

**Placeholders:** ninguno de contenido/código. `bootVersion` deliberadamente omitido. `TU_USUARIO` es el mismo placeholder ya rastreado desde la Fase 0. El `<HASH>` de `V3__seed_admin_user.sql` (Task 1 Step 9) no es un placeholder sin resolver — el paso da el código exacto para generarlo y sustituirlo antes de continuar, es la única forma correcta de obtener un hash BCrypt real (no se puede inventar uno que funcione).

**Consistencia de tipos/nombres:** `TaskService`/`TaskServiceImpl` con la firma `(TaskRequest, User)` usada idénticamente en `TaskController` (Task 3) y en la explicación de `roles-y-autorizacion.md` (Task 6); `UserPrincipal.getUser()` usado igual en `TaskController` (Task 3) y `spring-security-basico.md`; `JwtService.generateToken(String, Role)`/`isValid(String)`/`extractUsername(String)` con la misma firma en `JwtServiceTest` (Task 2), `AuthController` (Task 2) y `JwtAuthenticationFilter` (Task 2); `TaskResponse`/`UserSummary` con los mismos campos en su definición (Tasks 3-4) y en las páginas de docs que muestran ejemplos de respuesta (Task 6); rutas `/auth/register`, `/auth/login`, `/tasks`, `/tasks/{id}`, `/admin/users` consistentes entre controllers, tests y docs; credenciales del admin seed (`admin`/`admin12345`) idénticas en `V3__seed_admin_user.sql` (Task 1), `AuthControllerTest`/`TaskControllerTest`/`AdminControllerTest` (Tasks 2-4), `README.md` (Task 4) y `probar-endpoints-protegidos.md` (Task 6); puerto Postgres `5434` consistente en `docker-compose.yml`, `application.yml` y `README.md` (todos Task 1/4); `JAVA_HOME=/c/jdk-23.0.1` usado consistentemente en todas las invocaciones de `mvnw`.
