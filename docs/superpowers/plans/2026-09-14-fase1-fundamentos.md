# Fase 1 — Fundamentos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir al sitio la primera fase de contenido real de Spring Boot — 5 páginas sobre DI/beans, REST, capas, validación y manejo de errores — junto con su ejemplo ejecutable (`examples/01-fundamentos`, una API REST de gestión de tareas construida capa por capa), integrado en el CI existente.

**Architecture:** Mismo patrón que la Fase 0: contenido Docusaurus en `docs-site/docs/01-fundamentos/` (categoría ya existente) más un proyecto Maven/Spring Boot independiente en `examples/01-fundamentos/`, añadido a la matriz de `examples-ci.yml`. El ejemplo usa una arquitectura de 3 capas (controller → service → repository en memoria) con `records` de Java 21 para el modelo y las peticiones.

**Tech Stack:** Spring Boot (versión estable actual de `start.spring.io`, sin fijar un número — ver Global Constraints), Java 21, Maven Wrapper, Bean Validation (`jakarta.validation`), MockMvc para tests de integración.

**Spec:** [2026-09-14-fase1-fundamentos-design.md](../specs/2026-09-14-fase1-fundamentos-design.md)

## Global Constraints

- Idioma del contenido: **español**; código e identificadores en inglés/sin acentos, como en toda la documentación.
- `examples/01-fundamentos` es un proyecto Maven **independiente y autocontenido** (sin reactor multi-módulo), igual que `00-hello-world`.
- **No fijar la versión de Spring Boot a un número concreto.** La Fase 0 tuvo que sustituir `bootVersion=3.4.1` porque `start.spring.io` dejó de ofrecer la línea 3.x. Esta vez se omite el parámetro `bootVersion` en la llamada a Initializr para que tome su versión estable actual automáticamente.
- **El bit de ejecución de `mvnw` debe quedar correcto en el primer commit.** La Fase 0 tuvo un bug real (Critical) por commitear `mvnw` sin permiso de ejecución, lo que rompía el CI en Linux — Task 1 de este plan incluye el paso explícito para evitarlo.
- Nombres: groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks`, artifactId `tasks`, carpeta `examples/01-fundamentos`, nombre de clase principal `TasksApplication` (generado por Initializr a partir de `name=Tasks`).
- Máquina de desarrollo: usar `JAVA_HOME=/c/jdk-23.0.1` (bash) para toda invocación de `./mvnw` — el `java` del PATH por defecto es una versión antigua (JDK 8).
- Dominio del ejemplo: gestor de tareas — `Task(id, titulo, descripcion, completada)`.
- Fuera de alcance de esta fase: sin base de datos real (Fase 2), sin autenticación (Fase 3), **sin tests unitarios de service/repository separados del MockMvc de integración** (decisión explícita del spec — eso es tema de la Fase 6/Testing, no añadir aquí aunque parezca buena práctica).
- El `_category_.json` de `docs-site/docs/01-fundamentos/` (label, position 3) ya existe de la Fase 0 — no tocar.
- `docusaurus.config.js` tiene `numberPrefixParser: false` (fix de la Fase 0) — los nombres de archivo NO necesitan prefijo numérico para conservar su slug; el orden dentro de la categoría se controla con `sidebar_position` en el frontmatter de cada página.

---

## File Structure

```
docs-site/docs/01-fundamentos/
├── _category_.json                  # ya existe, no tocar
├── index.md                         # reescrito (era el placeholder "en construcción")
├── inyeccion-dependencias.md        # nuevo
├── controladores-rest.md            # nuevo
├── capas.md                         # nuevo
├── validacion.md                    # nuevo
└── manejo-errores.md                # nuevo

examples/01-fundamentos/             # nuevo proyecto Maven (vía Spring Initializr)
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/...
├── src/main/java/dev/springbootdocs/examples/tasks/
│   ├── TasksApplication.java        # generado por Initializr
│   ├── Task.java                    # record: id, titulo, descripcion, completada
│   ├── TaskRequest.java             # record de entrada, con @NotBlank en titulo
│   ├── TaskNotFoundException.java
│   ├── TaskRepository.java          # interfaz
│   ├── InMemoryTaskRepository.java
│   ├── TaskService.java             # interfaz
│   ├── TaskServiceImpl.java
│   ├── TaskController.java
│   ├── ApiError.java
│   ├── ValidationApiError.java
│   └── GlobalExceptionHandler.java
├── src/main/resources/application.yml
├── src/test/java/dev/springbootdocs/examples/tasks/
│   ├── TasksApplicationTests.java   # generado por Initializr
│   └── TaskControllerTest.java      # 9 tests MockMvc, TDD
└── README.md

.github/workflows/examples-ci.yml    # modificado: +1 línea en matrix.example
```

---

### Task 1: Bootstrap de `examples/01-fundamentos`

**Files:**
- Create: `examples/01-fundamentos/` (scaffold vía Spring Initializr)
- Create: `examples/01-fundamentos/src/main/resources/application.yml`
- Delete: `examples/01-fundamentos/src/main/resources/application.properties`

**Interfaces:**
- Produces: proyecto Maven con wrapper funcional y **bit de ejecución correcto en `mvnw`** desde el primer commit — Task 2 asume que `./mvnw` ya funciona.

- [ ] **Step 1: Generar el proyecto base desde Spring Initializr (sin fijar `bootVersion`)**

```bash
mkdir -p /d/Proyectos/spring-boot/examples/01-fundamentos
cd /d/Proyectos/spring-boot/examples
curl https://start.spring.io/starter.zip \
  -d dependencies=web,validation \
  -d type=maven-project \
  -d language=java \
  -d javaVersion=21 \
  -d groupId=dev.springbootdocs.examples \
  -d artifactId=tasks \
  -d name=Tasks \
  -d packageName=dev.springbootdocs.examples.tasks \
  -o tasks.zip
```

- [ ] **Step 2: Descomprimir en la carpeta final y limpiar el zip**

```bash
cd 01-fundamentos
unzip -o ../tasks.zip
rm ../tasks.zip
```

- [ ] **Step 3: Verificar que el wrapper funciona**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -v
```

Expected: imprime versión de Apache Maven y un JDK 21+ sin errores de descarga. Si el `pom.xml` generado usa nombres de dependencia distintos a `spring-boot-starter-webmvc`/`spring-boot-starter-webmvc-test` (por ejemplo si Initializr vuelve a servir una línea 3.x con `spring-boot-starter-web`/`spring-boot-starter-test`), no es un error — Task 2 debe adaptarse a los nombres reales generados, igual que se hizo en la Fase 0.

- [ ] **Step 4: Convertir `application.properties` a `application.yml`**

```bash
rm src/main/resources/application.properties
```

Crear `src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: 01-fundamentos
```

- [ ] **Step 5: Confirmar que el test de contexto por defecto pasa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS` (el único test en este punto es el `TasksApplicationTests.contextLoads` generado por Initializr).

- [ ] **Step 6: Preparar el commit y corregir el bit de ejecución de `mvnw` ANTES de commitear**

```bash
cd /d/Proyectos/spring-boot
git add examples/01-fundamentos
git update-index --chmod=+x examples/01-fundamentos/mvnw
git ls-files -s examples/01-fundamentos/mvnw
```

Expected: la última línea muestra el modo `100755` (no `100644`). Si muestra `100644`, repetir `git update-index --chmod=+x examples/01-fundamentos/mvnw` hasta que lo confirme — este es exactamente el bug Critical que la Fase 0 introdujo y corrigió después; no debe repetirse.

- [ ] **Step 7: Commit**

```bash
git commit -m "chore: bootstrap examples/01-fundamentos"
```

---

### Task 2: TDD — API REST de tareas (dominio, capas, validación, errores)

**Files:**
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/Task.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/TaskRequest.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/TaskNotFoundException.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/TaskRepository.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/InMemoryTaskRepository.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/TaskService.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/TaskServiceImpl.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/TaskController.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/ApiError.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/ValidationApiError.java`
- Create: `examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/GlobalExceptionHandler.java`
- Test: `examples/01-fundamentos/src/test/java/dev/springbootdocs/examples/tasks/TaskControllerTest.java`

**Interfaces:**
- Consumes: proyecto Maven funcional de Task 1.
- Produces: `POST /tasks`, `GET /tasks`, `GET /tasks/{id}`, `PUT /tasks/{id}`, `DELETE /tasks/{id}` — usados por Task 4 (docs) como referencia de código y por Task 5 (verificación final). Tipos `Task(id, titulo, descripcion, completada)` y `TaskRequest(titulo, descripcion, completada)` usados en los snippets de Task 4.

- [ ] **Step 1: Crear los tipos mínimos para que el test compile (`Task`, `TaskRequest`, `TaskNotFoundException`)**

`Task.java`:
```java
package dev.springbootdocs.examples.tasks;

public record Task(Long id, String titulo, String descripcion, boolean completada) {
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

- [ ] **Step 2: Escribir el test completo (`TaskControllerTest`) — RED**

```java
package dev.springbootdocs.examples.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
        createTaskAndGetId();

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
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

Note: si el `pom.xml` de Task 1 terminó usando `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` en vez de `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (porque Initializr sirvió una versión distinta de Spring Boot), ajustar el import de arriba al paquete real — comprobar inspeccionando el jar, igual que se hizo en la Fase 0.

- [ ] **Step 3: Ejecutar los tests y confirmar que fallan (RED)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskControllerTest
```

Expected: FAIL — todos los tests fallan con `404 Not Found` (no existe ningún mapeo para `/tasks` todavía). Si en cambio hay un error de compilación, revisar que `Task`/`TaskRequest`/`TaskNotFoundException` del Step 1 estén bien creados.

- [ ] **Step 4: Implementar el repositorio en memoria**

`TaskRepository.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    Task save(Task task);

    List<Task> findAll();

    Optional<Task> findById(Long id);

    void deleteById(Long id);
}
```

`InMemoryTaskRepository.java`:
```java
package dev.springbootdocs.examples.tasks;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTaskRepository implements TaskRepository {

    private final ConcurrentHashMap<Long, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Task save(Task task) {
        Long id = task.id() != null ? task.id() : nextId.getAndIncrement();
        Task saved = new Task(id, task.titulo(), task.descripcion(), task.completada());
        tasks.put(id, saved);
        return saved;
    }

    @Override
    public List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    @Override
    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public void deleteById(Long id) {
        tasks.remove(id);
    }
}
```

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

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public Task create(TaskRequest request) {
        Task task = new Task(null, request.titulo(), request.descripcion(), request.completada());
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
    public Task update(Long id, TaskRequest request) {
        findById(id);
        Task updated = new Task(id, request.titulo(), request.descripcion(), request.completada());
        return taskRepository.save(updated);
    }

    @Override
    public void delete(Long id) {
        findById(id);
        taskRepository.deleteById(id);
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

- [ ] **Step 7: Implementar el manejo de errores (`ApiError`, `ValidationApiError`, `GlobalExceptionHandler`)**

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
# 01-fundamentos

API REST de gestión de tareas — ejemplo ejecutable de la Fase 1 (Fundamentos) del sitio **Spring Boot desde cero**. Demuestra inyección de dependencias, controladores REST, capas (controller/service/repository), validación y manejo de errores. Sin base de datos real: el repositorio es en memoria.

## Requisitos

- JDK 21 o superior.

## Ejecutar

```bash
./mvnw spring-boot:run
```

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
```

- [ ] **Step 11: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/01-fundamentos
git commit -m "feat: add 01-fundamentos executable example (tasks CRUD API)"
```

---

### Task 3: Añadir `01-fundamentos` a la matriz de CI

**Files:**
- Modify: `.github/workflows/examples-ci.yml`

**Interfaces:**
- Consumes: `examples/01-fundamentos/mvnw` funcional (Task 1), con tests de Task 2.

- [ ] **Step 1: Añadir la nueva entrada a `matrix.example`**

En `.github/workflows/examples-ci.yml`, el bloque actual es:
```yaml
    strategy:
      matrix:
        example:
          - 00-hello-world
```

Cambiar a:
```yaml
    strategy:
      matrix:
        example:
          - 00-hello-world
          - 01-fundamentos
```

No modificar nada más del archivo (triggers, permissions, steps ya están correctos y son genéricos vía `matrix.example`).

- [ ] **Step 2: Validar sintaxis YAML localmente**

```bash
npx -y js-yaml .github/workflows/examples-ci.yml
```

Expected: imprime el YAML parseado sin errores.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/examples-ci.yml
git commit -m "ci: add 01-fundamentos to examples CI matrix"
```

---

### Task 4: Contenido de la Fase 1 (índice + 5 páginas)

**Files:**
- Modify: `docs-site/docs/01-fundamentos/index.md`
- Create: `docs-site/docs/01-fundamentos/inyeccion-dependencias.md`
- Create: `docs-site/docs/01-fundamentos/controladores-rest.md`
- Create: `docs-site/docs/01-fundamentos/capas.md`
- Create: `docs-site/docs/01-fundamentos/validacion.md`
- Create: `docs-site/docs/01-fundamentos/manejo-errores.md`

**Interfaces:**
- Consumes: código real de `examples/01-fundamentos` (Task 2) — los snippets de estas páginas deben coincidir con ese código.
- Produces: rutas `/docs/01-fundamentos/inyeccion-dependencias`, `.../controladores-rest`, `.../capas`, `.../validacion`, `.../manejo-errores` — verificadas en Task 5.

- [ ] **Step 1: Reescribir `index.md`**

```markdown
---
title: Fundamentos
---

# Fundamentos

Esta fase cubre lo esencial de Spring Boot antes de tocar temas más avanzados: cómo se conectan los objetos entre sí, cómo exponer una API REST, cómo organizar el código en capas, cómo validar las entradas y cómo devolver errores útiles.

## Contenido

1. [Inyección de dependencias y beans](./inyeccion-dependencias)
2. [Controladores REST](./controladores-rest)
3. [Capas: controller → service → repository](./capas)
4. [Validación](./validacion)
5. [Manejo de errores](./manejo-errores)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/01-fundamentos`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/01-fundamentos): una API REST de gestión de tareas (crear, listar, consultar, actualizar y eliminar), construida capa por capa según se explica en las páginas de arriba.

```bash
cd examples/01-fundamentos
./mvnw spring-boot:run
```
```

- [ ] **Step 2: Crear `inyeccion-dependencias.md`**

```markdown
---
title: Inyección de dependencias y beans
sidebar_position: 1
---

# Inyección de dependencias y beans

Un **bean** es un objeto que Spring crea y gestiona por ti, en vez de que tú lo instancies con `new`. Spring guarda los beans en un contenedor (el *application context*) y los conecta entre sí automáticamente según lo que cada uno necesita — eso es la **inyección de dependencias (DI)**.

## Declarar un bean

La forma más común es anotar una clase con un estereotipo:

- `@Component` — un bean genérico.
- `@Service` — un bean de lógica de negocio.
- `@Repository` — un bean de acceso a datos.
- `@RestController` — un bean que expone endpoints HTTP.

```java
@Service
public class TaskServiceImpl implements TaskService {
    // ...
}
```

Spring detecta estas clases automáticamente al arrancar (*component scanning*) y crea una instancia de cada una.

## Inyección por constructor

Cuando un bean necesita colaborar con otro, la forma recomendada es recibirlo como parámetro del constructor:

```java
@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // ...
}
```

Spring ve que `TaskServiceImpl` necesita un `TaskRepository`, busca un bean que implemente esa interfaz (`InMemoryTaskRepository`, en nuestro ejemplo) y se lo pasa automáticamente. No hace falta ninguna anotación adicional en el constructor cuando la clase tiene un único constructor.

**¿Por qué por constructor y no con `@Autowired` en un campo?** Porque así el objeto queda siempre en un estado válido (no puede existir sin su dependencia), el campo puede ser `final`, y es trivial de testear: en un test puedes construir `new TaskServiceImpl(unaImplementacionDePrueba)` sin necesitar Spring en absoluto.

## Programar contra interfaces

`TaskServiceImpl` depende de la interfaz `TaskRepository`, no de la clase concreta `InMemoryTaskRepository`. Esto es lo que hace que la inyección de dependencias tenga sentido: en el futuro (Fase 2) podremos sustituir `InMemoryTaskRepository` por una implementación respaldada por una base de datos real, sin tocar ni una línea de `TaskServiceImpl`.

Puedes ver el patrón completo en [`examples/01-fundamentos`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/01-fundamentos): `TaskController` depende de `TaskService`, y `TaskServiceImpl` depende de `TaskRepository` — cada capa solo conoce la interfaz de la capa siguiente.
```

- [ ] **Step 3: Crear `controladores-rest.md`**

```markdown
---
title: Controladores REST
sidebar_position: 2
---

# Controladores REST

Un controlador REST expone operaciones HTTP como métodos Java normales. Spring se encarga de convertir la petición HTTP en parámetros del método, y el valor que devuelve el método en la respuesta HTTP.

## `@RestController` y las anotaciones de mapeo

```java
@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks")
    public List<Task> findAll() {
        return taskService.findAll();
    }

    @GetMapping("/tasks/{id}")
    public Task findById(@PathVariable Long id) {
        return taskService.findById(id);
    }
}
```

- `@RestController` combina `@Controller` (es un bean gestionado por Spring) con `@ResponseBody` (lo que devuelva cada método se serializa directamente al cuerpo de la respuesta, en JSON por defecto).
- `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` mapean el método HTTP y la ruta. `{id}` en la ruta es una variable de plantilla, capturada con `@PathVariable`.

## Leer el cuerpo de la petición

Para los métodos que reciben datos (crear, actualizar), el cuerpo JSON de la petición se convierte automáticamente en un objeto Java con `@RequestBody`:

```java
@PostMapping("/tasks")
public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest request) {
    Task created = taskService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

(El `@Valid` se explica en la página de [Validación](./validacion) — aquí basta con saber que activa la validación automática de `request` antes de que el método se ejecute.)

## Controlar el código de estado con `ResponseEntity`

Cuando el código de estado por defecto (200 OK) no es el que quieres, envuelve la respuesta en un `ResponseEntity`:

```java
@DeleteMapping("/tasks/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    taskService.delete(id);
    return ResponseEntity.noContent().build();
}
```

`ResponseEntity.noContent().build()` devuelve **204 No Content**, el código estándar para "operación completada, sin cuerpo que devolver". `ResponseEntity.status(HttpStatus.CREATED)` (en el ejemplo de crear) devuelve **201 Created**.

El controlador completo, con los cinco endpoints (`POST`, `GET` lista, `GET` por id, `PUT`, `DELETE`), está en [`TaskController.java`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/01-fundamentos/src/main/java/dev/springbootdocs/examples/tasks/TaskController.java).
```

- [ ] **Step 4: Crear `capas.md`**

```markdown
---
title: 'Capas: controller → service → repository'
sidebar_position: 3
---

# Capas: controller → service → repository

Separar el código en capas evita que una sola clase mezcle responsabilidades distintas (HTTP, reglas de negocio, acceso a datos). Cada capa solo habla con la capa inmediatamente inferior, y lo hace a través de una interfaz.

## Las tres capas del ejemplo

```
TaskController  (capa web: recibe HTTP, valida, devuelve HTTP)
      ↓ usa
TaskService     (capa de negocio: reglas — "si no existe, lanzar TaskNotFoundException")
      ↓ usa
TaskRepository  (capa de datos: guardar/leer/borrar — hoy en memoria, mañana en una BBDD real)
```

**Controller** — solo se ocupa de HTTP: mapear rutas, leer el cuerpo de la petición, devolver el código de estado correcto. No contiene lógica de negocio.

```java
@GetMapping("/tasks/{id}")
public Task findById(@PathVariable Long id) {
    return taskService.findById(id);
}
```

**Service** — contiene las reglas de negocio. Por ejemplo, decidir qué pasa cuando pides una tarea que no existe:

```java
@Override
public Task findById(Long id) {
    return taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
}
```

El controller no sabe nada de esta decisión — solo delega en el service y confía en que, si algo va mal, se lanzará una excepción (ver [Manejo de errores](./manejo-errores)).

**Repository** — solo sabe guardar y recuperar datos. No sabe nada de HTTP ni de reglas de negocio:

```java
public interface TaskRepository {
    Task save(Task task);
    List<Task> findAll();
    Optional<Task> findById(Long id);
    void deleteById(Long id);
}
```

## Por qué importa esta separación

Cada capa se puede razonar, cambiar y (más adelante, en la Fase 6) testear de forma aislada:

- Puedes cambiar cómo se guardan las tareas (de memoria a una base de datos real, en la Fase 2) sin tocar el `TaskService` ni el `TaskController` — solo cambia la implementación de `TaskRepository`.
- Puedes cambiar una regla de negocio (por ejemplo, qué pasa si intentas borrar una tarea ya completada) sin tocar cómo se exponen los endpoints.
- El controller queda pequeño y fácil de leer: es solo el "traductor" entre HTTP y las operaciones del service.

Este es exactamente el mismo patrón que verás en el resto del roadmap — cada fase nueva añade capas o las reemplaza (por ejemplo, la Fase 2 sustituye `InMemoryTaskRepository` por una implementación con Spring Data JPA), pero el `TaskController` y el `TaskService` apenas cambian.
```

- [ ] **Step 5: Crear `validacion.md`**

```markdown
---
title: Validación
sidebar_position: 4
---

# Validación

Cuando un cliente envía datos incorrectos (por ejemplo, una tarea sin título), la API debería rechazarlos con un **400 Bad Request** claro, antes de que lleguen a la lógica de negocio. Spring Boot integra **Bean Validation** para esto.

## Anotar las restricciones

Las restricciones se declaran directamente en el objeto que representa la petición:

```java
public record TaskRequest(@NotBlank String titulo, String descripcion, boolean completada) {
}
```

`@NotBlank` (de `jakarta.validation.constraints`) exige que `titulo` no sea `null`, ni una cadena vacía, ni solo espacios en blanco. Bean Validation ofrece muchas más: `@NotNull`, `@Size(min=, max=)`, `@Email`, `@Min`/`@Max`, etc.

## Activar la validación con `@Valid`

Anotar el campo no basta: hay que decirle al controlador que valide antes de ejecutar el método, con `@Valid`:

```java
@PostMapping("/tasks")
public ResponseEntity<Task> create(@Valid @RequestBody TaskRequest request) {
    Task created = taskService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

Si `request` no cumple sus restricciones, Spring lanza una `MethodArgumentNotValidException` **antes** de que el cuerpo del método se ejecute — `taskService.create(request)` nunca llega a llamarse con datos inválidos.

## ¿Qué le llega al cliente?

Por defecto, esa excepción produciría una respuesta 400 genérica y poco útil. En [Manejo de errores](./manejo-errores) se explica cómo capturarla para devolver, en su lugar, algo como:

```json
{
  "status": 400,
  "message": "Datos de la petición inválidos",
  "timestamp": "2026-09-14T18:30:00Z",
  "errors": {
    "titulo": "must not be blank"
  }
}
```

De modo que quien consuma la API sepa exactamente qué campo falló y por qué.
```

- [ ] **Step 6: Crear `manejo-errores.md`**

```markdown
---
title: Manejo de errores
sidebar_position: 5
---

# Manejo de errores

Cuando algo va mal (una tarea que no existe, una petición inválida), la API debe devolver un código de estado correcto y un cuerpo JSON explicativo — no una traza de excepción cruda ni un 500 genérico.

## Excepciones propias

Cuando el service detecta un problema de negocio, lanza una excepción propia:

```java
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("No existe ninguna tarea con id " + id);
    }
}
```

```java
@Override
public Task findById(Long id) {
    return taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
}
```

El service no sabe (ni le importa) qué código HTTP corresponde a esta excepción — esa decisión vive en un único sitio, según se explica abajo.

## `@RestControllerAdvice`: un manejador centralizado

En vez de poner un `try/catch` en cada método del controller, Spring permite declarar manejadores globales con `@RestControllerAdvice`:

```java
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

Cada `@ExceptionHandler` intercepta un tipo de excepción concreto, lanzada desde *cualquier* controlador de la aplicación, y decide qué código de estado y qué cuerpo devolver.

## Un formato de error consistente

Ambos manejadores devuelven una forma parecida (`status`, `message`, `timestamp`), para que quien consuma la API pueda parsear los errores de manera uniforme sin importar cuál ocurrió:

```java
public record ApiError(int status, String message, String timestamp) { /* ... */ }

public record ValidationApiError(int status, String message, String timestamp, Map<String, String> errors) { /* ... */ }
```

`ValidationApiError` añade el mapa `errors` (campo → motivo del fallo), útil específicamente para errores de validación con varios campos incorrectos a la vez.

Con esto, el flujo completo de una petición inválida es: `@Valid` detecta el problema → lanza `MethodArgumentNotValidException` → `GlobalExceptionHandler` la captura → el cliente recibe un 400 con el detalle exacto de qué falló.
```

- [ ] **Step 7: Verificar el build del sitio**

```bash
cd docs-site
npm run build
```

Expected: `[SUCCESS] Generated static files in "build".` — sin enlaces rotos entre `index.md` y las 5 páginas nuevas.

- [ ] **Step 8: Commit**

```bash
cd /d/Proyectos/spring-boot
git add docs-site/docs/01-fundamentos
git commit -m "docs: add Fase 1 content (5 pages on DI, REST, layers, validation, errors)"
```

---

### Task 5: Verificación final de la Fase 1

**Files:** ninguno nuevo — solo verificación.

- [ ] **Step 1: Build limpio del sitio**

```bash
cd docs-site
rm -rf build .docusaurus
npm run build
```

Expected: éxito, cero enlaces rotos.

- [ ] **Step 2: Tests limpios del ejemplo**

```bash
cd examples/01-fundamentos
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B clean verify
```

Expected: `BUILD SUCCESS`, 10/10 tests.

- [ ] **Step 3: Confirmar el bit de ejecución de `mvnw` (repetir la comprobación de Task 1)**

```bash
git ls-files -s examples/01-fundamentos/mvnw
```

Expected: modo `100755`.

- [ ] **Step 4: Verificar las 5 rutas nuevas y el orden del sidebar**

```bash
cd docs-site
npm run serve &
sleep 3
for path in 01-fundamentos 01-fundamentos/inyeccion-dependencias 01-fundamentos/controladores-rest 01-fundamentos/capas 01-fundamentos/validacion 01-fundamentos/manejo-errores; do
  curl -s -o /dev/null -w "%{http_code} /docs/$path\n" "http://localhost:3000/spring-boot-docs/docs/$path"
done
kill %1
```

Expected: `200` para las 6 rutas (la de índice y las 5 páginas). Comprobar también en el HTML servido (`curl -s http://localhost:3000/spring-boot-docs/docs/01-fundamentos | grep -o 'sidebar[^"]*'` o inspección manual del `build/`) que las 5 páginas aparecen en el sidebar en el orden: Inyección de dependencias y beans → Controladores REST → Capas → Validación → Manejo de errores.

- [ ] **Step 5: Confirmar CI matrix actualizada**

```bash
cd /d/Proyectos/spring-boot
grep -A2 "matrix:" .github/workflows/examples-ci.yml
```

Expected: la lista incluye tanto `00-hello-world` como `01-fundamentos`.

- [ ] **Step 6: Commit final si queda algo pendiente**

```bash
git status
git add -A
git commit -m "chore: Fase 1 complete — Fundamentos content + tasks CRUD example + CI" --allow-empty
```

---

## Self-Review

**Cobertura del spec:** estructura de contenido (índice + 5 páginas, Task 4), ejemplo ejecutable con las 3 capas + validación + manejo de errores (Task 1-2), integración en CI (Task 3), criterios de aceptación — build sin broken links, `mvn verify` en CI, sidebar en orden (Task 5). Fuera de alcance respetado: sin tests unitarios de service/repository separados (Task 2 solo tiene `TaskControllerTest` vía MockMvc, tal como exige el spec), sin base de datos real, sin autenticación.

**Placeholders:** ninguno de contenido/código. El único valor deliberadamente no fijado es la versión de Spring Boot (Task 1 omite `bootVersion` a propósito, según Global Constraints) — no es un placeholder, es la lección aprendida de la Fase 0 aplicada.

**Consistencia de tipos/nombres:** `Task(id, titulo, descripcion, completada)` y `TaskRequest(titulo, descripcion, completada)` usados de forma idéntica en Task 2 (implementación) y Task 4 (snippets de documentación); rutas `/tasks`, `/tasks/{id}` consistentes entre controller, tests y docs; `TaskNotFoundException`, `ApiError`, `ValidationApiError`, `GlobalExceptionHandler` con los mismos nombres y campos en el código (Task 2) y en la página de manejo de errores (Task 4); `JAVA_HOME=/c/jdk-23.0.1` usado consistentemente en todas las invocaciones de `mvnw`, igual que en la Fase 0.
