# Fase 2 — Persistencia — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir a la Fase 1 su continuación natural — 4 páginas sobre Docker Compose/Postgres, Flyway, Spring Data JPA y probar con datos reales — junto con un nuevo ejemplo ejecutable (`examples/02-persistencia`) que reimplementa la misma API de tareas de la Fase 1, esta vez respaldada por PostgreSQL real vía Spring Data JPA, con el esquema versionado por Flyway.

**Architecture:** Mismo patrón que las Fases 0-1: contenido Docusaurus en `docs-site/docs/02-persistencia/` (categoría ya existente) más un proyecto Maven/Spring Boot independiente en `examples/02-persistencia/`, añadido a la matriz de `examples-ci.yml`. El ejemplo reutiliza el dominio de tareas de la Fase 1 pero sustituye el repositorio en memoria por `JpaRepository`, con Postgres real (vía `docker-compose.yml`) para ejecución y H2 en memoria (vía el classpath de test de Spring Boot, sin necesitar Docker) para los tests automáticos.

**Tech Stack:** Spring Boot (versión estable actual de `start.spring.io`, sin fijar un número), Java 21, Maven Wrapper, Spring Data JPA, PostgreSQL, Flyway, H2 (solo para tests), MockMvc.

**Spec:** [2026-09-14-fase2-persistencia-design.md](../specs/2026-09-14-fase2-persistencia-design.md)

## Global Constraints

- Idioma del contenido: **español**; código e identificadores en inglés/sin acentos.
- `examples/02-persistencia` es un proyecto Maven **independiente y autocontenido** — no modifica `examples/01-fundamentos`.
- **No fijar la versión de Spring Boot.** Omitir `bootVersion` en la llamada a Initializr.
- **El bit de ejecución de `mvnw` debe verificarse explícitamente antes de cada commit** (`git update-index --chmod=+x mvnw` + `git ls-files -s` mostrando `100755`).
- **Limpiar el `pom.xml` generado por Initializr** de boilerplate vacío (`<description/>`, `<url/>`, `<licenses>`, `<developers>`, `<scm>`) como paso explícito de Task 1 — no dejarlo para una revisión final.
- Nombres: groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks` (mismo paquete que la Fase 1 — refuerza "es el mismo código, evolucionado"; sin colisión posible porque son proyectos Maven independientes), artifactId `tasks-jpa`, carpeta `examples/02-persistencia`, clase principal `TasksJpaApplication` (de `name=TasksJpa`).
- **Refinamiento de implementación sobre el spec:** el spec describe el perfil de test como `application-test.yml` + `@ActiveProfiles("test")`. Este plan usa en su lugar `src/test/resources/application.yml` — un archivo que Spring Boot recoge automáticamente durante los tests (tiene prioridad sobre `src/main/resources/application.yml` en el classpath de test), sin necesitar ninguna anotación. Mismo resultado (H2 en los tests, sin Docker), pero más robusto: evita que el test de contexto por defecto de Initializr (que no lleva `@ActiveProfiles`) intente conectar contra el Postgres real inexistente en este entorno.
- `spring.jpa.hibernate.ddl-auto: validate` en ambos perfiles (main y test) — el esquema lo gestiona exclusivamente Flyway; Hibernate solo valida que las entidades coincidan.
- Puerto de Postgres en el host: `5433` (evita chocar con una instalación local en el `5432` por defecto).
- **Docker no está disponible en este entorno** (`docker --version` → comando no encontrado). Ninguna tarea de este plan puede verificar `docker compose up` contra Postgres real — la verificación de `docker-compose.yml` se limita a validar su sintaxis YAML. La verificación real con Postgres queda como pendiente explícito para el usuario (Task 5 lo documenta).
- Máquina de desarrollo: usar `JAVA_HOME=/c/jdk-23.0.1` (bash) para toda invocación de `./mvnw`.
- Fuera de alcance: sin Testcontainers (Fase 6), sin autenticación (Fase 3), sin migraciones de datos complejas más allá de la tabla inicial, sin tuning de pool de conexiones.
- El `_category_.json` de `docs-site/docs/02-persistencia/` (position 4) ya existe — no tocar. `numberPrefixParser: false` está activo site-wide — los nombres de archivo de las páginas NO llevan prefijo numérico; el orden lo da `sidebar_position`.

---

## File Structure

```
docs-site/docs/02-persistencia/
├── _category_.json                   # ya existe, no tocar
├── index.md                          # reescrito
├── docker-compose-postgres.md        # nuevo
├── flyway.md                         # nuevo
├── spring-data-jpa.md                # nuevo
└── probar-con-datos-reales.md        # nuevo

examples/02-persistencia/             # nuevo proyecto Maven (vía Spring Initializr)
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/...
├── docker-compose.yml
├── src/main/java/dev/springbootdocs/examples/tasks/
│   ├── TasksJpaApplication.java      # generado por Initializr
│   ├── Task.java                     # @Entity (id, titulo, descripcion, completada)
│   ├── TaskRequest.java              # record de entrada, @NotBlank en titulo
│   ├── TaskNotFoundException.java
│   ├── TaskRepository.java           # interface extends JpaRepository<Task, Long>
│   ├── TaskService.java              # interfaz
│   ├── TaskServiceImpl.java          # @Transactional en escrituras
│   ├── TaskController.java
│   ├── ApiError.java
│   ├── ValidationApiError.java
│   └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.yml               # datasource Postgres real, ddl-auto: validate
│   └── db/migration/V1__create_tasks_table.sql
├── src/test/resources/
│   └── application.yml               # datasource H2, ddl-auto: validate
├── src/test/java/dev/springbootdocs/examples/tasks/
│   ├── TasksJpaApplicationTests.java # generado por Initializr
│   └── TaskControllerTest.java       # 9 tests MockMvc, TDD
└── README.md

.github/workflows/examples-ci.yml     # modificado: +1 línea en matrix.example
```

---

### Task 1: Bootstrap de `examples/02-persistencia`

**Files:**
- Create: `examples/02-persistencia/` (scaffold vía Spring Initializr)
- Modify: `examples/02-persistencia/pom.xml` (limpieza de boilerplate)
- Create: `examples/02-persistencia/docker-compose.yml`
- Create: `examples/02-persistencia/src/main/resources/db/migration/V1__create_tasks_table.sql`
- Create: `examples/02-persistencia/src/main/resources/application.yml`
- Create: `examples/02-persistencia/src/test/resources/application.yml`
- Delete: `examples/02-persistencia/src/main/resources/application.properties`

**Interfaces:**
- Produces: proyecto Maven con wrapper funcional (bit de ejecución correcto), esquema `tasks` creado por Flyway contra H2 en el classpath de test, listo para que Task 2 añada la entidad `Task` y el resto de capas.

- [ ] **Step 1: Generar el proyecto base desde Spring Initializr (sin fijar `bootVersion`)**

```bash
mkdir -p /d/Proyectos/spring-boot/examples/02-persistencia
cd /d/Proyectos/spring-boot/examples
curl https://start.spring.io/starter.zip \
  -d dependencies=web,validation,data-jpa,postgresql,flyway,h2 \
  -d type=maven-project \
  -d language=java \
  -d javaVersion=21 \
  -d groupId=dev.springbootdocs.examples \
  -d artifactId=tasks-jpa \
  -d name=TasksJpa \
  -d packageName=dev.springbootdocs.examples.tasks \
  -o tasks-jpa.zip
```

- [ ] **Step 2: Descomprimir en la carpeta final y limpiar el zip**

```bash
cd 02-persistencia
unzip -o ../tasks-jpa.zip
rm ../tasks-jpa.zip
```

- [ ] **Step 3: Verificar que el wrapper funciona**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -v
```

Expected: imprime versión de Apache Maven y un JDK 21+ sin errores. Si el `pom.xml` generado usa nombres de dependencia distintos a los esperados por la Fase 1 (`spring-boot-starter-webmvc`, etc.) porque Initializr sirvió una versión distinta de Spring Boot, no es un error — Task 2 debe adaptarse a la realidad del proyecto generado, no asumir que sigue igual.

- [ ] **Step 4: Corregir el bit de ejecución de `mvnw` — hacerlo ahora, antes de cualquier otro paso**

```bash
cd /d/Proyectos/spring-boot
git add examples/02-persistencia
git update-index --chmod=+x examples/02-persistencia/mvnw
git ls-files -s examples/02-persistencia/mvnw
```

Expected: la última línea muestra el modo `100755`. Si muestra `100644`, repetir el `git update-index --chmod=+x` hasta confirmarlo.

- [ ] **Step 5: Limpiar el `pom.xml` de boilerplate vacío de Initializr**

Abrir `examples/02-persistencia/pom.xml`. Eliminar por completo los elementos vacíos que Initializr genera (`<url/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>`, el `<scm>` vacío) y sustituir `<description/>` por una descripción real:

```xml
<description>API REST de gestión de tareas con Spring Data JPA, PostgreSQL y Flyway.</description>
```

No tocar `groupId`, `artifactId`, `version`, `name`, `parent`, `properties`, `dependencies` ni `build`.

- [ ] **Step 6: Convertir `application.properties` a `application.yml` (perfil principal, Postgres real)**

```bash
rm src/main/resources/application.properties
```

Crear `src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: 02-persistencia
  datasource:
    url: jdbc:postgresql://localhost:5433/tasks
    username: tasks
    password: tasks
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
```

- [ ] **Step 7: Crear el perfil de test (H2, sin Docker)**

Crear `src/test/resources/application.yml` (Spring Boot lo usa automáticamente durante los tests en vez del de `src/main/resources`, sin necesitar ninguna anotación):
```yaml
spring:
  application:
    name: 02-persistencia
  datasource:
    url: jdbc:h2:mem:tasks;MODE=PostgreSQL
    username: sa
    password:
  flyway:
    enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
```

- [ ] **Step 8: Crear la migración Flyway**

Crear `src/main/resources/db/migration/V1__create_tasks_table.sql`:
```sql
CREATE TABLE tasks (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    descripcion VARCHAR(1000),
    completada BOOLEAN NOT NULL DEFAULT FALSE
);
```

- [ ] **Step 9: Crear `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: tasks
      POSTGRES_USER: tasks
      POSTGRES_PASSWORD: tasks
    ports:
      - "5433:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

- [ ] **Step 10: Validar la sintaxis YAML de `docker-compose.yml`**

```bash
npx -y js-yaml docker-compose.yml
```

Expected: imprime el YAML parseado sin errores. (Docker no está disponible en este entorno — no se puede validar más allá de la sintaxis; ver Global Constraints.)

- [ ] **Step 11: Confirmar que el test de contexto por defecto pasa usando H2 — esto ya ejercita la migración Flyway de verdad**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`. El único test en este punto es `TasksJpaApplicationTests.contextLoads`, pero como Flyway está activo, este paso ya confirma que `V1__create_tasks_table.sql` es SQL válido y se aplica correctamente contra H2 en modo PostgreSQL — antes de que exista ninguna entidad JPA.

- [ ] **Step 12: Commit**

```bash
git add examples/02-persistencia
git commit -m "chore: bootstrap examples/02-persistencia (Postgres, Flyway, H2 tests)"
```

---

### Task 2: TDD — API de tareas con Spring Data JPA

**Files:**
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/Task.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/TaskRequest.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/TaskNotFoundException.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/TaskRepository.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/TaskService.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/TaskServiceImpl.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/TaskController.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/ApiError.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/ValidationApiError.java`
- Create: `examples/02-persistencia/src/main/java/dev/springbootdocs/examples/tasks/GlobalExceptionHandler.java`
- Test: `examples/02-persistencia/src/test/java/dev/springbootdocs/examples/tasks/TaskControllerTest.java`

**Interfaces:**
- Consumes: proyecto Maven de Task 1 con Flyway/H2 funcionando.
- Produces: `POST /tasks`, `GET /tasks`, `GET /tasks/{id}`, `PUT /tasks/{id}`, `DELETE /tasks/{id}` — usados por Task 4 (docs) como referencia de código. `Task` ahora es una `@Entity` con getters/setters (no un record como en la Fase 1); `TaskRepository` no tiene implementación escrita a mano.

- [ ] **Step 1: Crear los tipos mínimos para que el test compile (`Task`, `TaskRequest`, `TaskNotFoundException`)**

`Task.java`:
```java
package dev.springbootdocs.examples.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    protected Task() {
    }

    public Task(String titulo, String descripcion, boolean completada) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.completada = completada;
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
}
```

`TaskRequest.java`:
```java
package dev.springbootdocs.examples.tasks;

import jakarta.validation.constraints.NotBlank;

public record TaskRequest(@NotBlank String titulo, String descripcion, boolean completada) {
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

Nota: dado que ahora hay una `@Entity` (aunque todavía sin repositorio ni controlador), el arranque del contexto validará automáticamente que `Task` coincide con la tabla `tasks` creada por Flyway en Task 1 (`ddl-auto: validate`) — si algo no coincide, fallará aquí, antes de escribir el test.

- [ ] **Step 2: Escribir el test completo (`TaskControllerTest`) — RED**

```java
package dev.springbootdocs.examples.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String validRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new TaskRequest("Comprar leche", "2 litros", false));
    }

    private Long createTaskAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createTask_returnsCreated() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.titulo").value("Comprar leche"))
                .andExpect(jsonPath("$.completada").value(false));
    }

    @Test
    void createTask_withBlankTitulo_returnsBadRequest() throws Exception {
        String json = objectMapper.writeValueAsString(new TaskRequest("", "sin título", false));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.titulo").exists());
    }

    @Test
    void listTasks_includesCreatedTask() throws Exception {
        Long id = createTaskAndGetId();

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].titulo").value("Comprar leche"));
    }

    @Test
    void getTaskById_whenExists_returnsTask() throws Exception {
        Long id = createTaskAndGetId();

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.titulo").value("Comprar leche"));
    }

    @Test
    void getTaskById_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/tasks/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateTask_whenExists_returnsUpdatedTask() throws Exception {
        Long id = createTaskAndGetId();
        String updateJson = objectMapper.writeValueAsString(
                new TaskRequest("Comprar pan", "integral", true));

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Comprar pan"))
                .andExpect(jsonPath("$.completada").value(true));
    }

    @Test
    void updateTask_whenNotFound_returns404() throws Exception {
        String updateJson = objectMapper.writeValueAsString(
                new TaskRequest("No existe", null, false));

        mockMvc.perform(put("/tasks/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_whenExists_thenReturns404OnFollowUpGet() throws Exception {
        Long id = createTaskAndGetId();

        mockMvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/tasks/{id}", 999999L))
                .andExpect(status().isNotFound());
    }
}
```

Nota: el import `tools.jackson.databind.ObjectMapper` y `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` son los correctos para Spring Boot 4.x (lección de la Fase 1 — Jackson 3 vive bajo `tools.jackson`, no `com.fasterxml.jackson`). Si Initializr sirvió una versión distinta en Task 1, verificar los paquetes reales (`unzip -l`/`javap` sobre el jar, no adivinar) y ajustar.

- [ ] **Step 3: Ejecutar los tests y confirmar que fallan (RED)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskControllerTest
```

Expected: FAIL — todos los tests fallan con `404 Not Found` (no existe ningún mapeo para `/tasks` todavía). El contexto debe arrancar sin problemas (Flyway + `Task` ya validado contra el esquema).

- [ ] **Step 4: Implementar el repositorio con Spring Data JPA**

`TaskRepository.java`:
```java
package dev.springbootdocs.examples.tasks;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}
```

Sin implementación escrita a mano: Spring Data genera la implementación en tiempo de ejecución. Comparar con `InMemoryTaskRepository` de la Fase 1 (`examples/01-fundamentos`) — esa clase entera desaparece.

- [ ] **Step 5: Implementar el service**

`TaskService.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.List;

public interface TaskService {

    Task create(TaskRequest request);

    List<Task> findAll();

    Task findById(Long id);

    Task update(Long id, TaskRequest request);

    void delete(Long id);
}
```

`TaskServiceImpl.java`:
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
    public Task create(TaskRequest request) {
        Task task = new Task(request.titulo(), request.descripcion(), request.completada());
        return taskRepository.save(task);
    }

    @Override
    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    @Override
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Override
    @Transactional
    public Task update(Long id, TaskRequest request) {
        Task task = findById(id);
        task.setTitulo(request.titulo());
        task.setDescripcion(request.descripcion());
        task.setCompletada(request.completada());
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Task task = findById(id);
        taskRepository.delete(task);
    }
}
```

- [ ] **Step 6: Implementar el controller**

```java
package dev.springbootdocs.examples.tasks;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest request) {
        Task created = taskService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/tasks")
    public List<Task> findAll() {
        return taskService.findAll();
    }

    @GetMapping("/tasks/{id}")
    public Task findById(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @PutMapping("/tasks/{id}")
    public Task update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 7: Implementar el manejo de errores**

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

`GlobalExceptionHandler.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiError> handleTaskNotFound(TaskNotFoundException ex) {
        ApiError error = ApiError.of(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
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

- [ ] **Step 8: Ejecutar los tests y confirmar que pasan (GREEN)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskControllerTest
```

Expected: PASS — 9/9 tests.

- [ ] **Step 9: Ejecutar la suite completa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, 10 tests en total (9 de `TaskControllerTest` + 1 `contextLoads`).

- [ ] **Step 10: Escribir el `README.md` del ejemplo**

```markdown
# 02-persistencia

API REST de gestión de tareas — ejemplo ejecutable de la Fase 2 (Persistencia) del sitio **Spring Boot desde cero**. Mismo dominio que `01-fundamentos`, pero ahora con Spring Data JPA, PostgreSQL y migraciones versionadas con Flyway en vez de un repositorio en memoria.

## Requisitos

- JDK 21 o superior.
- Docker (para levantar Postgres). Los tests NO necesitan Docker — usan H2 en memoria automáticamente.

## Levantar la base de datos

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5433` (usuario/contraseña/base de datos: `tasks`).

## Ejecutar

```bash
./mvnw spring-boot:run
```

Flyway crea el esquema automáticamente al arrancar.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/tasks` | Crear una tarea |
| GET | `/tasks` | Listar todas las tareas |
| GET | `/tasks/{id}` | Obtener una tarea |
| PUT | `/tasks/{id}` | Actualizar una tarea |
| DELETE | `/tasks/{id}` | Eliminar una tarea |

## Tests

```bash
./mvnw test
```

Usan H2 en memoria (ver `src/test/resources/application.yml`) — no requieren Docker ni Postgres.
```

- [ ] **Step 11: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/02-persistencia
git commit -m "feat: add 02-persistencia executable example (Spring Data JPA + Postgres + Flyway)"
```

---

### Task 3: Añadir `02-persistencia` a la matriz de CI

**Files:**
- Modify: `.github/workflows/examples-ci.yml`

**Interfaces:**
- Consumes: `examples/02-persistencia/mvnw` funcional (Task 1), con tests de Task 2 que no necesitan Docker.

- [ ] **Step 1: Añadir la nueva entrada a `matrix.example`**

El bloque actual es:
```yaml
    strategy:
      matrix:
        example:
          - 00-hello-world
          - 01-fundamentos
```

Cambiar a:
```yaml
    strategy:
      matrix:
        example:
          - 00-hello-world
          - 01-fundamentos
          - 02-persistencia
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
git commit -m "ci: add 02-persistencia to examples CI matrix"
```

---

### Task 4: Contenido de la Fase 2 (índice + 4 páginas)

**Files:**
- Modify: `docs-site/docs/02-persistencia/index.md`
- Create: `docs-site/docs/02-persistencia/docker-compose-postgres.md`
- Create: `docs-site/docs/02-persistencia/flyway.md`
- Create: `docs-site/docs/02-persistencia/spring-data-jpa.md`
- Create: `docs-site/docs/02-persistencia/probar-con-datos-reales.md`

**Interfaces:**
- Consumes: código real de `examples/02-persistencia` (Task 2) — los snippets deben coincidir con ese código.
- Produces: rutas `/docs/02-persistencia/docker-compose-postgres`, `.../flyway`, `.../spring-data-jpa`, `.../probar-con-datos-reales` — verificadas en Task 5.

- [ ] **Step 1: Reescribir `index.md`**

```markdown
---
title: Persistencia
---

# Persistencia

En la [Fase 1](/docs/01-fundamentos) guardábamos las tareas en un mapa en memoria: útil para aprender capas, pero los datos desaparecían al reiniciar la aplicación. Esta fase sustituye ese repositorio por persistencia real: PostgreSQL como base de datos, Spring Data JPA para no escribir SQL a mano, y Flyway para versionar el esquema como código.

## Contenido

1. [Docker Compose y Postgres](./docker-compose-postgres)
2. [Migraciones con Flyway](./flyway)
3. [Spring Data JPA](./spring-data-jpa)
4. [Probar la app con datos reales](./probar-con-datos-reales)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/02-persistencia`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/02-persistencia): la misma API de tareas de la Fase 1, ahora respaldada por Postgres.

```bash
docker compose up -d
cd examples/02-persistencia
./mvnw spring-boot:run
```
```

- [ ] **Step 2: Crear `docker-compose-postgres.md`**

```markdown
---
title: Docker Compose y Postgres
sidebar_position: 1
---

# Docker Compose y Postgres

Para desarrollar contra una base de datos real sin instalar PostgreSQL en tu máquina, usamos Docker Compose: describe qué contenedores necesitas en un archivo YAML, y un solo comando los levanta.

## El archivo `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: tasks
      POSTGRES_USER: tasks
      POSTGRES_PASSWORD: tasks
    ports:
      - "5433:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

- `image: postgres:16` — usa la imagen oficial de PostgreSQL 16 desde Docker Hub.
- `environment` — variables que la imagen oficial usa para crear la base de datos, el usuario y la contraseña la primera vez que arranca.
- `ports: "5433:5432"` — mapea el puerto 5433 de tu máquina al 5432 del contenedor (el puerto por defecto de Postgres). Usamos 5433 para no chocar si ya tienes Postgres instalado localmente en el 5432.
- `volumes` — un volumen con nombre para que los datos sobrevivan si detienes y vuelves a levantar el contenedor.

## Levantar y detener

```bash
docker compose up -d
```

El flag `-d` lo ejecuta en segundo plano. Para detenerlo:

```bash
docker compose down
```

(Añade `-v` a `down` si además quieres borrar el volumen y empezar de cero.)

## Cómo se conecta la aplicación

`application.yml` apunta directamente a este contenedor:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/tasks
    username: tasks
    password: tasks
```

Mientras el contenedor esté levantado, `./mvnw spring-boot:run` se conecta contra él automáticamente — no hace falta ninguna configuración adicional.
```

- [ ] **Step 3: Crear `flyway.md`**

```markdown
---
title: Migraciones con Flyway
sidebar_position: 2
---

# Migraciones con Flyway

Cuando varias personas (o varios entornos: tu máquina, CI, producción) comparten una base de datos, necesitas una forma reproducible de crear y evolucionar su esquema. Flyway resuelve esto con **migraciones**: archivos SQL versionados que se aplican en orden, una sola vez cada uno.

## Convención de nombres

Flyway busca migraciones en `src/main/resources/db/migration/` con el patrón `V<versión>__<descripción>.sql` (dos guiones bajos entre versión y descripción):

```
src/main/resources/db/migration/
└── V1__create_tasks_table.sql
```

## La primera migración

```sql
CREATE TABLE tasks (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    descripcion VARCHAR(1000),
    completada BOOLEAN NOT NULL DEFAULT FALSE
);
```

SQL estándar: crea la tabla `tasks` con las mismas columnas que la entidad `Task` (ver [Spring Data JPA](./spring-data-jpa)) va a mapear.

## Cuándo se ejecutan

Flyway se ejecuta automáticamente al arrancar la aplicación (`spring.flyway.enabled: true`, activado por defecto en cuanto añades la dependencia). Comprueba qué migraciones ya se aplicaron (las registra en una tabla propia, `flyway_schema_history`) y aplica solo las nuevas, en orden por número de versión.

## Por qué esto importa más que "simplemente crear la tabla a mano"

- El esquema queda versionado junto al código, en git — puedes ver exactamente cómo evolucionó.
- Cualquier persona que clone el repo y arranque la aplicación obtiene el mismo esquema, sin pasos manuales.
- Los tests de este ejemplo ejecutan **esta misma migración** contra una base de datos H2 en memoria (ver [Probar la app con datos reales](./probar-con-datos-reales)) — así que cada vez que corres `./mvnw test`, también estás comprobando que la migración es válida.

Cuando en el futuro necesites cambiar el esquema (añadir una columna, por ejemplo), no modificas `V1__create_tasks_table.sql` — añades `V2__<algo>.sql` con el cambio. Las migraciones ya aplicadas no se tocan nunca.
```

- [ ] **Step 4: Crear `spring-data-jpa.md`**

```markdown
---
title: Spring Data JPA
sidebar_position: 3
---

# Spring Data JPA

En la Fase 1, `TaskRepository` era una interfaz con una implementación (`InMemoryTaskRepository`) que tú escribías a mano, usando un `ConcurrentHashMap`. Con Spring Data JPA, esa implementación deja de hacer falta.

## La entidad: de `record` a `@Entity`

En la Fase 1, `Task` era un `record` inmutable. Una entidad JPA no puede serlo: necesita un constructor sin argumentos (JPA lo usa internamente para reconstruir objetos desde la base de datos) y campos mutables (para que Hibernate pueda actualizar sus valores).

```java
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

    protected Task() {
    }

    public Task(String titulo, String descripcion, boolean completada) {
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.completada = completada;
    }

    // getters y setters
}
```

- `@Entity` + `@Table(name = "tasks")` — le dice a JPA que esta clase se mapea a la tabla `tasks` (la que crea la migración de Flyway).
- `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` — el campo `id` es la clave primaria, generada por la base de datos (coincide con `GENERATED BY DEFAULT AS IDENTITY` en el SQL de la migración).
- El constructor protegido sin argumentos es un requisito técnico de JPA, no se usa directamente desde tu código.

## El repositorio: cero implementación

```java
public interface TaskRepository extends JpaRepository<Task, Long> {
}
```

Eso es todo. `JpaRepository<Task, Long>` ya trae `save`, `findAll`, `findById`, `delete` y muchos más métodos, implementados por Spring Data en tiempo de ejecución — nunca escribes una clase `TaskRepositoryImpl`. Comparado con `InMemoryTaskRepository` de la Fase 1 (una clase completa con un mapa, un contador atómico y cuatro métodos), esta interfaz vacía hace exactamente el mismo trabajo, pero contra una base de datos real.

Esto es precisamente lo que hacía posible la inyección de dependencias de la Fase 1: `TaskServiceImpl` sigue dependiendo de la interfaz `TaskRepository`, nunca de una implementación concreta — así que pudimos cambiar completamente cómo se guardan los datos sin tocar el service.

## `@Transactional`

Los métodos de escritura del service ahora se anotan con `@Transactional`:

```java
@Override
@Transactional
public Task create(TaskRequest request) {
    Task task = new Task(request.titulo(), request.descripcion(), request.completada());
    return taskRepository.save(task);
}
```

Con una base de datos real, una operación puede implicar varias sentencias SQL que deben tener éxito juntas o fallar juntas (por ejemplo, si más adelante `create` también tuviera que escribir en otra tabla relacionada). `@Transactional` envuelve el método en una transacción: si algo falla a mitad, todo se revierte. Con el repositorio en memoria de la Fase 1 esto no hacía falta — no había nada que revertir.
```

- [ ] **Step 5: Crear `probar-con-datos-reales.md`**

```markdown
---
title: Probar la app con datos reales
sidebar_position: 4
---

# Probar la app con datos reales

Con Postgres, Flyway, la entidad y el repositorio ya en su sitio, así es como se conecta todo.

## Levantar la base de datos y la aplicación

```bash
docker compose up -d
cd examples/02-persistencia
./mvnw spring-boot:run
```

Al arrancar, Flyway aplica `V1__create_tasks_table.sql` contra el Postgres del contenedor (si no se había aplicado ya), y la aplicación queda escuchando en `http://localhost:8080`.

## Probar los endpoints

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"titulo": "Comprar leche", "descripcion": "2 litros", "completada": false}'

curl http://localhost:8080/tasks
```

Si detienes la aplicación y vuelves a arrancarla (sin borrar el volumen de Docker), las tareas siguen ahí — a diferencia de la Fase 1, donde se perdían al reiniciar.

## Cómo funcionan los tests sin Docker

`./mvnw test` **no necesita Docker ni Postgres**. Spring Boot usa automáticamente `src/test/resources/application.yml` en vez del de `src/main/resources` durante los tests, y ese archivo apunta a H2 (una base de datos en memoria) en modo de compatibilidad con PostgreSQL:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:tasks;MODE=PostgreSQL
    username: sa
    password:
  flyway:
    enabled: true
```

Flyway sigue activo en los tests: la misma migración `V1__create_tasks_table.sql` que crea la tabla en Postgres también la crea en H2. Así, `./mvnw test` comprueba de verdad que la migración es válida, no solo que el mapeo de la entidad es correcto — y ni tu máquina ni el CI necesitan Docker instalado para que la suite pase.

## `ddl-auto: validate`

Tanto `application.yml` como el de test fijan `spring.jpa.hibernate.ddl-auto: validate`. Esto le dice a Hibernate: "no crees ni modifiques el esquema tú — eso es trabajo de Flyway. Solo comprueba que la entidad `Task` coincide con la tabla real, y si no coincide, falla con un mensaje claro en vez de intentar arreglarlo por su cuenta." Es la combinación recomendada siempre que uses Flyway (o Liquibase) junto con JPA.
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
git add docs-site/docs/02-persistencia
git commit -m "docs: add Fase 2 content (Docker Compose, Flyway, Spring Data JPA, testing)"
```

---

### Task 5: Verificación final de la Fase 2

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
cd examples/02-persistencia
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B clean verify
```

Expected: `BUILD SUCCESS`, 10/10 tests.

- [ ] **Step 3: Confirmar el bit de ejecución de `mvnw`**

```bash
git ls-files -s examples/02-persistencia/mvnw
```

Expected: modo `100755`.

- [ ] **Step 4: Validar `docker-compose.yml` de nuevo (sintaxis, no ejecución real — Docker no disponible)**

```bash
npx -y js-yaml examples/02-persistencia/docker-compose.yml
```

Expected: parsea sin errores.

- [ ] **Step 5: Verificar las 5 rutas nuevas y el orden del sidebar**

```bash
cd docs-site
npm run serve &
sleep 3
for path in 02-persistencia 02-persistencia/docker-compose-postgres 02-persistencia/flyway 02-persistencia/spring-data-jpa 02-persistencia/probar-con-datos-reales; do
  curl -s -o /dev/null -w "%{http_code} /docs/$path\n" "http://localhost:3000/spring-boot-docs/docs/$path"
done
kill %1
```

Expected: `200` para las 5 rutas. Comprobar en el HTML servido o en `build/` que las 4 páginas aparecen en el sidebar en el orden: Docker Compose y Postgres → Flyway → Spring Data JPA → Probar con datos reales.

- [ ] **Step 6: Confirmar CI matrix actualizada**

```bash
cd /d/Proyectos/spring-boot
grep -A3 "matrix:" .github/workflows/examples-ci.yml
```

Expected: la lista incluye `00-hello-world`, `01-fundamentos` y `02-persistencia`.

- [ ] **Step 7: Documentar el pendiente real (Docker/Postgres) — no simularlo**

Confirmar en el reporte de esta tarea, explícitamente, que:
- Ninguna verificación de este plan ha levantado Postgres real ni ha ejecutado `docker compose up` contra un daemon Docker de verdad (no disponible en este entorno).
- La única garantía real sobre la migración Flyway viene de que se ejecuta también contra H2 en los tests (Step 2) — no es lo mismo que confirmarla contra Postgres real.
- Esto queda como pendiente explícito para un humano con Docker instalado, igual que la revisión visual de las Fases 0-1.

- [ ] **Step 8: Commit final si queda algo pendiente**

```bash
git status
git add -A
git commit -m "chore: Fase 2 complete — Persistencia content + JPA/Postgres/Flyway example + CI" --allow-empty
```

---

## Self-Review

**Cobertura del spec:** estructura de contenido (índice + 4 páginas, Task 4), ejemplo ejecutable con JPA/Postgres/Flyway/H2 (Task 1-2), integración en CI (Task 3), criterios de aceptación — build sin broken links, `mvn verify` con H2 sin Docker, sidebar en orden, `mvnw` con bit de ejecución correcto, `pom.xml` limpio (Task 5). Las 5 lecciones técnicas del spec aplicadas explícitamente: sin `bootVersion` fijo (Task 1 Step 1), bit de `mvnw` verificado antes del commit (Task 1 Step 4, antes que cualquier otra cosa), `pom.xml` limpiado como paso propio (Task 1 Step 5), nota explícita sobre verificar paquetes reales de Spring Boot 4.x en vez de asumir (Task 1 Step 3, Task 2 Step 2), `examples/02-persistencia` completamente independiente de `examples/01-fundamentos` (ningún task lo toca). `ddl-auto: validate` y el pendiente honesto de Docker, ambos añadidos en la segunda revisión del spec, están cubiertos (Task 1 Steps 6-7, Task 5 Step 7).

**Placeholders:** ninguno de contenido/código. `bootVersion` deliberadamente omitido (lección de fases previas). `TU_USUARIO` en los enlaces de GitHub de las páginas de contenido es el mismo placeholder ya rastreado desde la Fase 0, no uno nuevo.

**Consistencia de tipos/nombres:** `Task(id, titulo, descripcion, completada)` con getters/setters usado idénticamente en Task 2 (entidad) y Task 4 (snippets de `spring-data-jpa.md`); `TaskRequest(titulo, descripcion, completada)` idéntico a la Fase 1; rutas `/tasks`, `/tasks/{id}` consistentes entre controller, tests y docs; `V1__create_tasks_table.sql` con las mismas columnas en Task 1 (migración), Task 2 (entidad) y Task 4 (página de Flyway); `JAVA_HOME=/c/jdk-23.0.1` usado consistentemente en todas las invocaciones de `mvnw`.
