# Fase 6 — Testing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Añadir un nuevo ejemplo ejecutable (`examples/06-testing`) y 4 páginas de contenido que enseñan JUnit avanzado, Mockito y Testcontainers, usando el API de tareas de la Fase 4 (auth JWT + caché) portado verbatim como sustrato — el foco pedagógico es la disciplina de testing en sí, no el dominio.

**Architecture:** Mismo patrón que las Fases 0-4: contenido Docusaurus en `docs-site/docs/06-testing/` (categoría ya existente, `position: 8`) más un proyecto Maven/Spring Boot independiente en `examples/06-testing/`, añadido a la matriz de `examples-ci.yml`. El código fuente (`src/main`) y los tests ya existentes de `examples/04-cache-redis` se copian **byte a byte** (no se retipean) a `examples/06-testing`, con solo puerto/nombre de base de datos/artifactId adaptados. Sobre esa base ya verificada se añaden tres piezas nuevas: un test parametrizado (JUnit), un test unitario puro con Mockito, y un test de integración con Testcontainers (Postgres + Redis reales).

**Tech Stack:** Spring Boot 4.1.1 (mismo que Fase 4 — no se vuelve a generar vía Initializr sin fijar versión porque este proyecto nace de un `cp` del ya generado, no de Initializr; ver Task 1), Java 21, Maven Wrapper, JUnit 5 (`junit-jupiter-params` para parametrizados), Mockito 5.23.0 + `mockito-junit-jupiter`, AssertJ 3.27.7, Testcontainers (`org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql`, `com.redis:testcontainers-redis` 2.2.4), `spring-boot-testcontainers`, `maven-failsafe-plugin`.

**Spec:** [2026-09-23-fase6-testing-design.md](../specs/2026-09-23-fase6-testing-design.md)

## Global Constraints

- Idioma del contenido: **español**; código e identificadores en inglés/sin acentos salvo los ya establecidos en fases anteriores (`titulo`, `descripcion`, `completada`).
- `examples/06-testing` es un proyecto Maven **independiente y autocontenido** — no modifica `examples/04-cache-redis`.
- Nombres: groupId `dev.springbootdocs.examples`, packageName `dev.springbootdocs.examples.tasks`, artifactId `tasks-testing`, `name=TasksTesting`, carpeta `examples/06-testing`, clase principal `TasksTestingApplication`.
- Puerto de Postgres en el host: `5436` (Fase 2 usa `5433`, Fase 3 usa `5434`, Fase 4 usa `5435`). Nombre de base de datos/usuario: `tasks_testing`. Redis usa su puerto por defecto `6379` sin remapear.
- **El bit de ejecución de `mvnw` debe verificarse explícitamente antes del primer commit** (`git update-index --chmod=+x mvnw` + `git ls-files -s` mostrando `100755`).
- **`mockito-core`, `mockito-junit-jupiter`, `junit-jupiter-params` y `assertj-core` YA están en el classpath de test** — llegan transitivamente vía `spring-boot-starter-cache-test` → `spring-boot-starter-test` (verificado con `mvn dependency:tree -Dscope=test` contra el proyecto real de Fase 4 antes de escribir este plan: `mockito-core:5.23.0`, `mockito-junit-jupiter:5.23.0`, `junit-jupiter-params:6.0.3`, `assertj-core:3.27.7`). **No añadir ninguna de estas cuatro dependencias al `pom.xml`** — ya están ahí, añadirlas de nuevo sin versión gestionada puede introducir un conflicto de versión.
- **Coordenadas reales de Testcontainers para Spring Boot 4.1.1 (verificado generando un proyecto de prueba real en start.spring.io con el dependency id `testcontainers`, NO asumido de memoria — los nombres cambiaron respecto a versiones antiguas de Testcontainers):**
  - `org.testcontainers:testcontainers-junit-jupiter` (no `org.testcontainers:junit-jupiter`).
  - `org.testcontainers:testcontainers-postgresql` (no `org.testcontainers:postgresql`).
  - Ambas sin versión explícita — gestionadas por el BOM de Testcontainers que `spring-boot-testcontainers` importa transitivamente.
  - `com.redis:testcontainers-redis:2.2.4` (verificado en Maven Central) SÍ necesita versión explícita — no está en ningún BOM de Spring Boot. Clase real: `com.redis.testcontainers.RedisContainer` (verificado inspeccionando el jar), constructor `RedisContainer(DockerImageName)`, sin genéricos.
  - **Corregido durante Task 1 (hallazgo real, no anticipado al escribir el plan):** en Testcontainers 2.0.5 (la versión que resuelve este proyecto), `PostgreSQLContainer` vive en `org.testcontainers.postgresql` (no en `org.testcontainers.containers`, el paquete clásico) y **ya no es una clase genérica autorreferenciada** — es `PostgreSQLContainer(DockerImageName)`, sin `<>`. Verificado con `javap` contra el `.class` real dentro del jar resuelto por este mismo proyecto, y confirmado por el propio código que Spring Initializr generó en `TestcontainersConfiguration.java` (Task 1), que ya usa esta forma. El clásico `org.testcontainers.containers.PostgreSQLContainer<SELF>` sigue existiendo en el jar (probablemente por compatibilidad) pero no es el que este plan usa.
- `maven-failsafe-plugin` **ya está gestionado por `spring-boot-starter-parent`** (goals `integration-test`+`verify` pre-configurados — verificado en el POM del parent). Basta con declarar `<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-failsafe-plugin</artifactId></plugin>` sin versión ni `<executions>` propias.
- **Docker no está disponible en este entorno.** `./mvnw test` (Surefire) debe pasar en verde localmente. `./mvnw verify` completo (con Failsafe → Testcontainers) **fallará localmente por falta de Docker — esto es esperado**, se usa `./mvnw verify -DskipITs` para confirmar compilación/empaquetado. La verificación real de los tests `*IT.java` llega en `examples-ci.yml` (GitHub Actions, `ubuntu-latest`, Docker preinstalado) tras el push.
- Máquina de desarrollo: usar `JAVA_HOME=/c/jdk-23.0.1` (bash) para toda invocación de `./mvnw`.
- Fuera de alcance: mutation testing, tests de carga/performance, contract testing, cobertura de código (JaCoCo), optimización de contenedores Testcontainers (singleton pattern), cambios al workflow de CI más allá de añadir `06-testing` a la matriz, refresh tokens/OAuth2/OIDC, cachear `findAll`.

---

## File Structure

```
docs-site/docs/06-testing/
├── _category_.json                     # ya existe (position: 8), no tocar
├── index.md                            # reescrito
├── junit-avanzado.md                   # nuevo
├── mockito-y-tests-unitarios.md        # nuevo
├── testcontainers.md                   # nuevo
└── cuando-usar-cada-uno.md             # nuevo

examples/06-testing/                    # nuevo proyecto Maven (Initializr + copia byte a byte de Fase 4)
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/...
├── docker-compose.yml                  # postgres (5436) + redis:7-alpine
├── src/main/java/dev/springbootdocs/examples/tasks/
│   ├── TasksTestingApplication.java    # generado por Initializr, no se toca
│   ├── (31 clases más)                 # copiadas byte a byte de examples/04-cache-redis (Task 2)
├── src/main/resources/
│   ├── application.yml                 # adaptado: puerto 5436, db tasks_testing
│   ├── application-h2.yml              # adaptado: ruta de archivo H2
│   └── db/migration/
│       ├── V1__create_users_table.sql  # copiada byte a byte
│       ├── V2__create_tasks_table.sql  # copiada byte a byte
│       └── V3__seed_admin_user.sql     # copiada byte a byte (mismo hash, ya verificado)
├── src/test/resources/
│   └── application.yml                 # adaptado: nombre de app
├── src/test/java/dev/springbootdocs/examples/tasks/
│   ├── TasksTestingApplicationTests.java  # generado por Initializr — Task 1 le quitó @Import(TestcontainersConfiguration.class) para correr sobre H2 sin Docker
│   ├── TestcontainersConfiguration.java   # generado por Initializr, scaffolding de conveniencia sin caller en el build automatizado (ver hallazgo de revisión de Task 1) — no se toca
│   ├── TestTasksTestingApplication.java   # generado por Initializr, idem — no se toca
│   ├── JwtServiceTest.java             # copiado byte a byte
│   ├── GlobalExceptionHandlerTest.java # copiado byte a byte
│   ├── AuthControllerTest.java         # copiado byte a byte
│   ├── CacheBehaviorTest.java          # copiado byte a byte
│   ├── CacheValueSerializationTest.java # copiado byte a byte
│   ├── AdminControllerTest.java        # copiado byte a byte
│   ├── TaskControllerTest.java         # copiado, LUEGO modificado (Task 3 — parametrizado)
│   ├── TaskServiceImplTest.java        # NUEVO (Task 4 — Mockito puro)
│   └── TaskApiIT.java                  # NUEVO (Task 5 — Testcontainers)
└── README.md                           # nuevo

.github/workflows/examples-ci.yml       # modificado: +1 línea en matrix.example
```

---

### Task 1: Bootstrap de `examples/06-testing`

**Files:**
- Create: `examples/06-testing/` (scaffold vía Spring Initializr)
- Modify: `examples/06-testing/pom.xml` (limpieza + jjwt + Testcontainers + failsafe)
- Create: `examples/06-testing/docker-compose.yml`
- Create: `examples/06-testing/src/main/resources/db/migration/V1__create_users_table.sql`
- Create: `examples/06-testing/src/main/resources/db/migration/V2__create_tasks_table.sql`
- Create: `examples/06-testing/src/main/resources/db/migration/V3__seed_admin_user.sql`
- Create: `examples/06-testing/src/main/resources/application.yml`
- Create: `examples/06-testing/src/main/resources/application-h2.yml`
- Create: `examples/06-testing/src/test/resources/application.yml`
- Delete: `examples/06-testing/src/main/resources/application.properties`

**Interfaces:**
- Produces: proyecto Maven con wrapper funcional, esquema `users`+`tasks` (con admin seed), Testcontainers listo para usar, listo para que Task 2 copie el código Java de Fase 4.

- [ ] **Step 1: Generar el proyecto base desde Spring Initializr (sin fijar `bootVersion`, incluyendo `testcontainers`)**

```bash
mkdir -p /d/Proyectos/spring-boot/examples/06-testing
cd /d/Proyectos/spring-boot/examples
curl https://start.spring.io/starter.zip \
  -d dependencies=web,validation,data-jpa,postgresql,flyway,h2,security,cache,data-redis,testcontainers \
  -d type=maven-project \
  -d language=java \
  -d javaVersion=21 \
  -d groupId=dev.springbootdocs.examples \
  -d artifactId=tasks-testing \
  -d name=TasksTesting \
  -d packageName=dev.springbootdocs.examples.tasks \
  -o tasks-testing.zip
```

- [ ] **Step 2: Descomprimir en la carpeta final y limpiar el zip**

```bash
cd 06-testing
unzip -o ../tasks-testing.zip
rm ../tasks-testing.zip
```

- [ ] **Step 3: Verificar que el wrapper funciona**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -v
```

Expected: imprime versión de Apache Maven y un JDK 21+ sin errores. Si el `pom.xml` generado usa nombres de dependencia distintos a los esperados, no es un error — adaptarse a la realidad del proyecto generado (mismo criterio que en fases anteriores).

- [ ] **Step 4: Corregir el bit de ejecución de `mvnw` — antes de cualquier otro paso**

```bash
cd /d/Proyectos/spring-boot
git add examples/06-testing
git update-index --chmod=+x examples/06-testing/mvnw
git ls-files -s examples/06-testing/mvnw
```

Expected: la última línea muestra el modo `100755`. Si muestra `100644`, repetir hasta confirmarlo.

- [ ] **Step 5: Limpiar el `pom.xml` de boilerplate vacío de Initializr, añadir jjwt, y confirmar las dependencias de Testcontainers generadas**

Abrir `examples/06-testing/pom.xml`. Eliminar `<url/>`, `<licenses><license/></licenses>`, `<developers><developer/></developers>`, el `<scm>` vacío, y sustituir `<description/>` por:

```xml
<description>API REST de gestión de tareas — ejemplo de testing (JUnit avanzado, Mockito, Testcontainers), sobre el mismo dominio con auth JWT y caché de la Fase 4.</description>
```

Añadir la propiedad de versión de jjwt dentro de `<properties>`:

```xml
<jjwt.version>0.12.6</jjwt.version>
```

Añadir estas tres dependencias dentro de `<dependencies>`:

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

Confirmar que Initializr ya generó estas tres (dependency id `testcontainers` + `postgresql` juntos):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

Si Initializr generó nombres distintos a estos tres, **no asumir que este plan tiene razón** — usar los nombres reales generados (mismo criterio que con `bootVersion`), documentando el cambio en el commit.

Añadir manualmente (no la genera Initializr):

```xml
<dependency>
    <groupId>com.redis</groupId>
    <artifactId>testcontainers-redis</artifactId>
    <version>2.2.4</version>
    <scope>test</scope>
</dependency>
```

Añadir dentro de `<build><plugins>` (el `spring-boot-maven-plugin` ya generado por Initializr se mantiene tal cual):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
</plugin>
```

Sin versión ni `<executions>` propias — `spring-boot-starter-parent` ya gestiona este plugin con los goals `integration-test`+`verify` pre-configurados.

No tocar `groupId`, `artifactId`, `version`, `name`, `parent`, `properties.java.version` ni el resto de `dependencies`.

- [ ] **Step 6: Crear `application.yml` (perfil principal, Postgres + Redis reales, puerto 5436)**

```bash
rm src/main/resources/application.properties
```

Crear `src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: 06-testing
  datasource:
    url: jdbc:postgresql://localhost:5436/tasks_testing
    username: tasks_testing
    password: tasks_testing
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

- [ ] **Step 7: Crear el perfil `h2` (sin Docker, sin Redis)**

Crear `src/main/resources/application-h2.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/tasks_testing;MODE=PostgreSQL
    username: sa
    password:
  cache:
    type: simple
```

- [ ] **Step 8: Crear el perfil de test (H2 en memoria + caché simple) — igual patrón que Fases 2-4**

Crear `src/test/resources/application.yml`:
```yaml
spring:
  application:
    name: 06-testing
  datasource:
    url: jdbc:h2:mem:tasks_testing;MODE=PostgreSQL
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

Nota: este perfil de test es el que usan Surefire (`./mvnw test`) y los tests unitarios/parametrizados. Los tests `*IT.java` de Testcontainers (Task 5) **no** usan este perfil — `@ServiceConnection` sobrescribe `spring.datasource.*`/`spring.data.redis.*` dinámicamente contra los contenedores reales.

- [ ] **Step 9: Copiar las migraciones de Fase 4 byte a byte (mismo esquema, incluido el hash BCrypt del admin ya verificado — no se regenera)**

```bash
cd /d/Proyectos/spring-boot
cp examples/04-cache-redis/src/main/resources/db/migration/V1__create_users_table.sql \
   examples/06-testing/src/main/resources/db/migration/V1__create_users_table.sql
cp examples/04-cache-redis/src/main/resources/db/migration/V2__create_tasks_table.sql \
   examples/06-testing/src/main/resources/db/migration/V2__create_tasks_table.sql
cp examples/04-cache-redis/src/main/resources/db/migration/V3__seed_admin_user.sql \
   examples/06-testing/src/main/resources/db/migration/V3__seed_admin_user.sql
```

Expected: los tres archivos existen en `examples/06-testing/src/main/resources/db/migration/`. El hash BCrypt de `V3` es el mismo que Fase 4 — `BCryptPasswordEncoder.matches("admin12345", hash)` no depende de en qué proyecto Maven se verifique, así que no hace falta regenerarlo (a diferencia de cuando Fase 4 lo generó por primera vez).

- [ ] **Step 10: Crear `docker-compose.yml` con Postgres (5436) y Redis**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: tasks_testing
      POSTGRES_USER: tasks_testing
      POSTGRES_PASSWORD: tasks_testing
    ports:
      - "5436:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  postgres-data:
```

- [ ] **Step 11: Validar la sintaxis YAML de `docker-compose.yml`**

```bash
cd examples/06-testing
npx -y js-yaml docker-compose.yml
```

Expected: imprime el YAML parseado sin errores.

- [ ] **Step 12: Confirmar que el test de contexto por defecto pasa usando H2 — ejercita las 3 migraciones reales**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify -DskipITs
```

Expected: `BUILD SUCCESS`. El único test en este punto es `TasksTestingApplicationTests.contextLoads` (generado por Initializr), pero Flyway ya aplica `V1`, `V2` y `V3` contra H2 en modo PostgreSQL.

- [ ] **Step 13: Commit**

```bash
git add examples/06-testing
git commit -m "chore: bootstrap examples/06-testing (users/tasks schema, Postgres, Redis, Testcontainers, Failsafe)"
```

---

### Task 2: Portar el código de Fase 4 byte a byte y confirmar el baseline en verde

Esta fase no rediseña el dominio — el spec es explícito: "se porta `examples/04-cache-redis` completo y verbatim". Este task copia los archivos directamente (no los retipea) para garantizar fidelidad byte a byte, y confirma que el port compila y todos los tests heredados siguen en verde antes de añadir nada nuevo.

**Files:**
- Create: 31 archivos en `examples/06-testing/src/main/java/dev/springbootdocs/examples/tasks/` (todos los de `examples/04-cache-redis` excepto `TasksCacheApplication.java`)
- Create: 6 archivos en `examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks/` (`AuthControllerTest`, `AdminControllerTest`, `CacheBehaviorTest`, `CacheValueSerializationTest`, `GlobalExceptionHandlerTest`, `JwtServiceTest`)
- Create: `examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks/TaskControllerTest.java` (copiado — Task 3 lo modifica después)

**Interfaces:**
- Consumes: proyecto de Task 1.
- Produces: `TaskServiceImpl`, `CachedTaskLookup`, `TaskRepository`, `TaskController`, `AuthController`, etc. — idénticos a Fase 4, listos para que Tasks 3-5 los usen sin modificarlos (excepto `TaskControllerTest`, que Task 3 sí modifica).

- [ ] **Step 1: Copiar todas las clases de `src/main/java` excepto la clase de aplicación (que ya generó Initializr con el nombre correcto)**

```bash
cd /d/Proyectos/spring-boot
SRC=examples/04-cache-redis/src/main/java/dev/springbootdocs/examples/tasks
DEST=examples/06-testing/src/main/java/dev/springbootdocs/examples/tasks

for f in "$SRC"/*.java; do
  name=$(basename "$f")
  if [ "$name" != "TasksCacheApplication.java" ]; then
    cp "$f" "$DEST/$name"
  fi
done

ls "$DEST" | wc -l
```

Expected: 32 archivos en `$DEST` (31 copiados + `TasksTestingApplication.java`, ya generado por Initializr en Task 1; corregido tras contar mal en la escritura original del plan — `examples/04-cache-redis` tiene 32 clases en total, no 29).

- [ ] **Step 2: Copiar los tests ya existentes de Fase 4, excepto el test de contexto (ya generado por Initializr) y `TaskControllerTest` (se copia igual, pero Task 3 lo modifica a continuación)**

```bash
SRC=examples/04-cache-redis/src/test/java/dev/springbootdocs/examples/tasks
DEST=examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks

for f in "$SRC"/*.java; do
  name=$(basename "$f")
  if [ "$name" != "TasksCacheApplicationTests.java" ]; then
    cp "$f" "$DEST/$name"
  fi
done

ls "$DEST" | wc -l
```

Expected: 10 archivos en `$DEST` (7 copiados + 3 ya generados por Initializr en Task 1: `TasksTestingApplicationTests.java`, y también `TestcontainersConfiguration.java`/`TestTasksTestingApplication.java` — estos dos últimos no anticipados al escribir el plan original, son scaffolding de conveniencia de Initializr para correr la app localmente con contenedores, sin caller en el build automatizado; ver hallazgo de la revisión de Task 1).

- [ ] **Step 3: Ejecutar los tests y confirmar que el port compila y pasa en verde tal cual, sin ningún cambio de comportamiento**

```bash
cd examples/06-testing
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify -DskipITs
```

Expected: `BUILD SUCCESS`, 36 tests en verde (igual conteo que el `mvn verify` final de Fase 4 — mismo código, mismo número de tests). Si algo falla, es un error de copia (ruta, permiso, archivo faltante) — no un problema de diseño, porque el código es idéntico al ya verificado en Fase 4.

- [ ] **Step 4: Commit**

```bash
git add examples/06-testing
git commit -m "chore: port examples/04-cache-redis source verbatim into examples/06-testing"
```

---

### Task 3: JUnit avanzado — parametrizar la validación de `TaskRequest` + página de contenido

**Files:**
- Modify: `examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks/TaskControllerTest.java`
- Create: `docs-site/docs/06-testing/junit-avanzado.md`

**Interfaces:**
- Consumes: `TaskControllerTest` (Task 2), `TaskRequest` (`titulo` con `@NotBlank @Size(max=255)`, `descripcion` con `@Size(max=1000)` — sin `@NotBlank`).
- Produces: ninguna interfaz nueva para otros tasks — este es contenido autocontenido.

- [ ] **Step 1: Reemplazar el único test de validación existente por un `@ParameterizedTest` + `@Nested` que cubre los tres casos reales de `TaskRequest`**

En `TaskControllerTest.java`, el método actual `createTask_withTituloOverMaxLength_returnsBadRequest` (líneas ~201-214) usa el nombre `victor`. `descripcion` > 1000 caracteres no tiene test hoy. Reemplazar ese único método por:

```java
@Nested
class TituloInvalido {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void tituloEnBlanco_returnsBadRequest(String tituloInvalido) throws Exception {
        assertTaskCreationRejected("wendy", tituloInvalido, "desc", "titulo");
    }

    @Test
    void tituloDemasiadoLargo_returnsBadRequest() throws Exception {
        assertTaskCreationRejected("xavier", "a".repeat(256), "desc", "titulo");
    }
}

@Nested
class DescripcionInvalida {

    @Test
    void descripcionDemasiadoLarga_returnsBadRequest() throws Exception {
        assertTaskCreationRejected("yolanda", "Titulo valido", "a".repeat(1001), "descripcion");
    }
}

private void assertTaskCreationRejected(
        String username, String titulo, String descripcion, String failingField) throws Exception {
    String token = registerAndLogin(username);
    String json = objectMapper.writeValueAsString(new TaskRequest(titulo, descripcion, false));

    mockMvc.perform(post("/tasks")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors." + failingField).exists());
}
```

`wendy`, `xavier` y `yolanda` son nombres no usados por ningún otro test del archivo (verificar con `grep -n 'registerAndLogin("' TaskControllerTest.java` antes de aplicar — todos los tests comparten el mismo contexto Spring/H2, así que un nombre repetido causaría un `409 Conflict` en el registro).

Añadir los imports nuevos junto a los ya existentes al principio del archivo:

```java
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
```

`@Nested` requiere que las clases internas **no sean `static`** (JUnit 5 las instancia como clases internas del test externo) y que el test externo (`TaskControllerTest`) no sea `final` — ya cumplido, no requiere cambios en la declaración de la clase.

- [ ] **Step 2: Ejecutar los tests de este archivo y confirmar que los tres casos nuevos pasan**

```bash
cd examples/06-testing
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskControllerTest
```

Expected: `BUILD SUCCESS`. El conteo de tests de esta clase sube en 4 respecto a Fase 4 (1 test de validación → 5: título en blanco/null/espacios agrupados en un solo método parametrizado con 3 invocaciones, más título largo, más descripción larga — JUnit cuenta cada invocación parametrizada como un test independiente).

- [ ] **Step 3: Escribir `docs-site/docs/06-testing/junit-avanzado.md`**

```markdown
---
title: JUnit avanzado
sidebar_position: 1
---

# JUnit avanzado

Hasta ahora, cada caso de validación de `TaskRequest` vivía en su propio método `@Test`, casi idéntico al anterior salvo por el dato de entrada. Esta página cubre las herramientas de JUnit 5 para dejar de repetir esa estructura: tests parametrizados, agrupación con `@Nested`, y el ciclo de vida de una clase de test.

## `@ParameterizedTest`

`TaskRequest` valida tres cosas: `titulo` no puede estar en blanco, `titulo` no puede superar 255 caracteres, `descripcion` no puede superar 1000. Antes de esta fase, solo el segundo caso tenía test. En vez de escribir tres métodos casi idénticos, un único método parametrizado cubre varias entradas:

```java
@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"   "})
void tituloEnBlanco_returnsBadRequest(String tituloInvalido) throws Exception {
    // el cuerpo del test recibe tituloInvalido: null, "", y "   " — tres ejecuciones
}
```

`@NullAndEmptySource` inyecta `null` y `""`; `@ValueSource` añade valores propios — aquí, un título que solo tiene espacios (que `@NotBlank` también rechaza, a diferencia de una simple comprobación de "no vacío"). JUnit ejecuta el método una vez por cada valor, cada una como un test independiente en el reporte — si una falla, se sabe exactamente cuál.

Para datos que no son un único valor por caso (por ejemplo, "esta entrada, más el campo que se espera que falle"), `@CsvSource` o `@MethodSource` permiten combinaciones más ricas — no se usan aquí porque `TaskRequest` no lo necesita, pero son la herramienta cuando un solo `@ValueSource` se queda corto.

## `@Nested` para agrupar por intención

```java
@Nested
class TituloInvalido {
    // tests sobre el campo titulo
}

@Nested
class DescripcionInvalida {
    // tests sobre el campo descripcion
}
```

Una clase interna no estática anotada `@Nested` agrupa tests relacionados bajo un mismo contexto — en el reporte aparecen anidados bajo el nombre de la clase (`TaskControllerTest > TituloInvalido > tituloEnBlanco_returnsBadRequest`), lo que hace más fácil ver de un vistazo qué se está probando cuando la clase de test crece. No cambia qué se ejecuta, solo cómo se organiza y se reporta.

## Ciclo de vida: `@BeforeEach` vs `@BeforeAll`

Un método `@BeforeEach` se ejecuta antes de **cada** test — es el que ya se viene usando implícitamente en este proyecto vía la inyección de `MockMvc`/`ObjectMapper` con `@Autowired` (Spring los reinyecta en cada instancia de test, una instancia por método por defecto). Un método `@BeforeAll` se ejecuta **una sola vez**, antes de todos los tests de la clase — debe ser `static` a menos que la clase use `@TestInstance(Lifecycle.PER_CLASS)`.

La diferencia importa para el costo y el aislamiento: algo que se pueda compartir sin efectos secundarios entre tests (por ejemplo, un contenedor de Testcontainers — ver [Testcontainers](./testcontainers)) va en `@BeforeAll`/un campo `static`, porque levantarlo una vez por clase es mucho más barato que una vez por test. Algo que un test pueda mutar y que otro test no deba heredar (como el estado de un mock) va en `@BeforeEach`.
```

- [ ] **Step 4: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks/TaskControllerTest.java \
        docs-site/docs/06-testing/junit-avanzado.md
git commit -m "feat: parametrize TaskRequest validation tests, add JUnit avanzado page"
```

---

### Task 4: Mockito — test unitario puro de `TaskServiceImpl` + página de contenido

**Files:**
- Create: `examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks/TaskServiceImplTest.java`
- Create: `docs-site/docs/06-testing/mockito-y-tests-unitarios.md`

**Interfaces:**
- Consumes: `TaskServiceImpl(TaskRepository, CachedTaskLookup)` (Task 2) — `findById(Long, User)` devuelve `TaskResponse`, lanza `TaskAccessDeniedException` si `!isOwner && !isAdmin`, delega en `cachedTaskLookup.findById(Long)` que lanza `TaskNotFoundException` si no existe. `Task(String titulo, String descripcion, boolean completada, User user)`. `User(String username, String password, Role role)` — el campo `id` es privado, generado por JPA, sin setter público.
- Produces: ninguna interfaz nueva para otros tasks.

- [ ] **Step 1: Escribir `TaskServiceImplTest.java` — RED primero**

```java
package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CachedTaskLookup cachedTaskLookup;

    private TaskServiceImpl taskService;

    private User owner;
    private User otherUser;
    private User admin;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(taskRepository, cachedTaskLookup);

        owner = new User("owner", "hash", Role.USER);
        ReflectionTestUtils.setField(owner, "id", 1L);

        otherUser = new User("other", "hash", Role.USER);
        ReflectionTestUtils.setField(otherUser, "id", 2L);

        admin = new User("admin", "hash", Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 3L);
    }

    @Test
    void findById_asOwner_returnsTask() {
        Task task = taskOwnedBy(owner, 10L);
        when(cachedTaskLookup.findById(10L)).thenReturn(task);

        TaskResponse response = taskService.findById(10L, owner);

        assertThat(response.titulo()).isEqualTo("Comprar pan");
    }

    @Test
    void findById_asDifferentUser_throwsAccessDenied() {
        Task task = taskOwnedBy(owner, 10L);
        when(cachedTaskLookup.findById(10L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.findById(10L, otherUser))
                .isInstanceOf(TaskAccessDeniedException.class);
    }

    @Test
    void findById_asAdmin_returnsTaskEvenWithoutOwnership() {
        Task task = taskOwnedBy(owner, 10L);
        when(cachedTaskLookup.findById(10L)).thenReturn(task);

        TaskResponse response = taskService.findById(10L, admin);

        assertThat(response.titulo()).isEqualTo("Comprar pan");
    }

    @Test
    void findById_whenTaskDoesNotExist_throwsNotFound() {
        when(cachedTaskLookup.findById(99L)).thenThrow(new TaskNotFoundException(99L));

        assertThatThrownBy(() -> taskService.findById(99L, owner))
                .isInstanceOf(TaskNotFoundException.class);
    }

    private Task taskOwnedBy(User user, Long id) {
        Task task = new Task("Comprar pan", "desc", false, user);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
```

`User.id`/`Task.id` son campos privados que JPA rellena con `@GeneratedValue` — sin un `EntityManager` real de por medio (este test no levanta contexto Spring ni base de datos), no hay forma de asignarlos con un setter público porque no existe. `ReflectionTestUtils.setField` (de `spring-test`, ya en el classpath) es el patrón estándar para forzar ese campo en un test unitario puro — vale la pena mencionarlo explícitamente en la página de contenido, porque es la primera vez que aparece en el proyecto.

- [ ] **Step 2: Ejecutar el test y confirmar que compila y pasa (no hay fase RED/GREED real aquí — la clase bajo test ya existe desde Fase 4, portada en Task 2; este test es nuevo pero no requiere cambios en `TaskServiceImpl`)**

```bash
cd examples/06-testing
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=TaskServiceImplTest
```

Expected: `BUILD SUCCESS`, 4/4 tests en verde. Si falla, revisar que `ReflectionTestUtils` resuelva el import correcto (`org.springframework.test.util.ReflectionTestUtils`, ya en el classpath de test — ver Global Constraints) y no una clase homónima de otra librería.

- [ ] **Step 3: Escribir `docs-site/docs/06-testing/mockito-y-tests-unitarios.md`**

```markdown
---
title: Mockito y tests unitarios
sidebar_position: 2
---

# Mockito y tests unitarios

Hasta esta fase, **todos** los tests del sitio han sido de integración: `@SpringBootTest` levanta el contexto completo de Spring, una base de datos real (H2), y se ejercita el API entero vía HTTP con MockMvc. Es una herramienta potente, pero no la única — y saber cuándo cambiar a un test unitario puro con Mockito es, en la práctica, uno de los criterios que menos se enseña bien. Antes de ver código, el criterio.

## Cuándo sí, cuándo no

Un test **unitario** con Mockito tiene sentido cuando la clase bajo prueba tiene lógica de negocio no trivial y sus dependencias son fáciles de sustituir (interfaces, sin `final`) — el objetivo es probar esa lógica aislada, sin pagar el costo de levantar un contexto Spring ni una base de datos.

Un test de **integración** (lo ya conocido: `@SpringBootTest` + MockMvc) tiene sentido cuando lo que hay que confirmar es que las piezas están bien conectadas — que Spring Security bloquea lo que debe bloquear, que una consulta JPA devuelve lo que la anotación dice que devuelve, que la serialización JSON no rompe nada. Eso no se puede mockear: hay que ejercitarlo de verdad.

`TaskServiceImpl.findById` es un buen candidato para Mockito: la lógica de ownership (¿puede este usuario ver esta tarea?) vive enteramente en Java, sin tocar la base de datos directamente — solo llama a `CachedTaskLookup`, una interfaz de una línea fácil de sustituir. Hasta ahora esa lógica solo se había probado indirectamente, vía HTTP con todo el contexto real.

## Errores comunes (y por qué importan)

- **Mockear todo, incluidos objetos simples.** Un `record` o una clase de valor sin lógica (como `TaskRequest`) no necesita mock — crear una instancia real es más simple y más fiel que simular su comportamiento.
- **Sobre-usar `verify()`.** Comprobar que un método se llamó exactamente una vez con exactamente estos argumentos es útil cuando ese llamado *es* el comportamiento a probar (por ejemplo, "se debe invalidar la caché al actualizar"). Usado en cada test, termina probando *cómo* está implementado el método en vez de *qué* devuelve — un refactor interno que no cambia el comportamiento rompe el test igualmente.
- **Confundir `@Mock`/`Mockito.mock()` con `@MockBean`/`@MockitoBean`.** `@Mock` (este test) crea un doble sin ningún Spring de por medio — rápido, aislado. `@MockBean`/`@MockitoBean` sustituye un bean *dentro* de un contexto Spring real (`@SpringBootTest` sigue arrancando) — resuelve un problema distinto: "quiero el contexto real, pero sin que este componente concreto llame a un servicio externo". Son herramientas para problemas distintos, no intercambiables.
- **No resetear stubs entre tests cuando hace falta.** `@ExtendWith(MockitoExtension.class)` crea mocks nuevos por cada método de test por defecto — no es necesario resetear a mano, pero si un mock se comparte a propósito entre tests (por ejemplo, vía un campo `static`), hay que ser explícito sobre por qué.

## El test

```java
@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CachedTaskLookup cachedTaskLookup;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(taskRepository, cachedTaskLookup);
    }

    @Test
    void findById_asDifferentUser_throwsAccessDenied() {
        Task task = taskOwnedBy(owner, 10L);
        when(cachedTaskLookup.findById(10L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.findById(10L, otherUser))
                .isInstanceOf(TaskAccessDeniedException.class);
    }

    // ...
}
```

`@Mock` crea el doble; `when(...).thenReturn(...)` programa su respuesta; `taskService` recibe esos dobles por constructor, como recibiría las implementaciones reales — **la clase bajo prueba no sabe que está siendo probada con mocks**. Ni `taskRepository` ni `cachedTaskLookup` tocan una base de datos: la aserción confirma únicamente la lógica de `requireAccess` dentro de `TaskServiceImpl`.

Un detalle que aparece la primera vez aquí: `User`/`Task` tienen un campo `id` que JPA rellena automáticamente al guardar en una base de datos real — en un test que nunca toca la base de datos, ese campo queda en `null` a menos que se fuerce con `ReflectionTestUtils.setField(objeto, "id", valor)` (de `spring-test`). No es una solución elegante, es un recordatorio honesto de que un test unitario puro a veces tiene que trabajar un poco más para simular lo que la infraestructura real da gratis.
```

- [ ] **Step 4: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks/TaskServiceImplTest.java \
        docs-site/docs/06-testing/mockito-y-tests-unitarios.md
git commit -m "feat: add Mockito unit test for TaskServiceImpl ownership logic, add Mockito page"
```

---

### Task 5: Testcontainers — test de integración contra Postgres y Redis reales + página de contenido

**Files:**
- Create: `examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks/TaskApiIT.java`
- Create: `docs-site/docs/06-testing/testcontainers.md`

**Interfaces:**
- Consumes: `RegisterRequest`, `LoginRequest`, `TaskRequest` (Task 2), rutas `/auth/register`, `/auth/login`, `/tasks`, `/tasks/{id}` (Task 2). `com.redis.testcontainers.RedisContainer` (constructor `RedisContainer(DockerImageName)`, no genéricos). `org.testcontainers.postgresql.PostgreSQLContainer` (constructor `PostgreSQLContainer(DockerImageName)`, **sin genéricos** — a diferencia de la clase clásica `org.testcontainers.containers.PostgreSQLContainer<SELF>`, esta (la que Testcontainers 2.0.5/Spring Initializr genera para este proyecto) ya no es una clase autorreferenciada; verificado inspeccionando el `.class` real del jar resuelto por este mismo proyecto — no asumir el paquete/firma clásicos de versiones anteriores de Testcontainers). `org.springframework.boot.testcontainers.service.connection.ServiceConnection`.
- Produces: ninguna interfaz nueva para otros tasks.

- [ ] **Step 1: Escribir `TaskApiIT.java` — sufijo `IT` obligatorio (Failsafe lo recoge, Surefire lo excluye por convención estándar de Maven, sin configuración extra)**

```java
package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullFlow_registerLoginCreateAndReadTask_throughRealPostgresAndRedis() throws Exception {
        String username = "itzel";
        String password = "password123";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        MvcResult createResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskRequest("Aprender Testcontainers", "desc", false))))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/tasks/{id}", taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // La lectura anterior pasó por CachedTaskLookup (@Cacheable) contra el Redis real
        // del contenedor: confirmarlo leyendo la clave directamente, con el mismo cliente
        // redis-cli que se usa para verificación manual en Fase 4.
        org.testcontainers.containers.Container.ExecResult keysResult =
                redis.execInContainer("redis-cli", "keys", "tasks::*");
        assertThat(keysResult.getStdout()).contains("tasks::" + taskId);
    }
}
```

Nota de import: hay dos clases distintas con el mismo nombre corto `Container` en Testcontainers — `org.testcontainers.containers.Container` (la interfaz que declara `ExecResult`, el tipo de retorno de `execInContainer`) y `org.testcontainers.junit.jupiter.Container` (la anotación `@Container` del ciclo de vida JUnit 5). Java no permite importar dos tipos distintos con el mismo nombre simple en el mismo archivo, así que solo se importa la anotación (`org.testcontainers.junit.jupiter.Container`, usada varias veces como `@Container`) y la interfaz se referencia con su nombre completo la única vez que hace falta (`org.testcontainers.containers.Container.ExecResult`), como en el código de arriba.

- [ ] **Step 2: Confirmar que compila (sin ejecutar — Docker no está disponible en este entorno)**

```bash
cd examples/06-testing
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test-compile
```

Expected: `BUILD SUCCESS`. Esto compila `TaskApiIT.java` sin ejecutarlo — Failsafe solo corre en la fase `integration-test`, que no se alcanza con `test-compile`.

- [ ] **Step 3: Confirmar que el resto del build (Surefire + empaquetado) sigue en verde sin ejecutar los `*IT.java`**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify -DskipITs
```

Expected: `BUILD SUCCESS`. `TaskApiIT` no se ejecuta (`-DskipITs`) pero sí se compila como parte de `test-compile`, confirmando que no hay errores de sintaxis/tipos. **No intentar `./mvnw verify` sin `-DskipITs` en este entorno — fallará por falta de Docker, es el comportamiento esperado (ver Global Constraints), no un bug a investigar.**

- [ ] **Step 4: Escribir `docs-site/docs/06-testing/testcontainers.md`**

```markdown
---
title: Testcontainers
sidebar_position: 3
---

# Testcontainers

Las Fases 2-4 solo pudieron probar contra H2 (en vez de Postgres) y `ConcurrentMapCacheManager` (en vez de Redis) — la única forma de tener una base de datos y una caché reales en un test automatizado, sin depender de que quien lo ejecute tenga Docker corriendo y configurado a mano. Testcontainers cierra esa brecha: levanta contenedores Docker reales, uno por dependencia, solo durante la ejecución de los tests, y los destruye al terminar.

## `@Testcontainers` + `@Container` + `@ServiceConnection`

```java
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    // ...
}
```

`@Testcontainers` activa el ciclo de vida de JUnit 5 para los campos `@Container`: cada contenedor se levanta antes de los tests de la clase y se destruye al terminar — al ser campos `static`, se comparten entre todos los métodos de test de esta clase (una sola vez, no uno por test). `@ServiceConnection` es la pieza que evita tener que cablear manualmente `spring.datasource.url`/`spring.data.redis.host` apuntando al puerto aleatorio que Docker asignó a cada contenedor: Spring Boot detecta el tipo de contenedor y configura esas propiedades automáticamente en tiempo de test. Sin `@ServiceConnection`, haría falta un `@DynamicPropertySource` manual — funciona, pero es exactamente el tipo de cableado a mano que esta anotación existe para evitar.

## Un test, infraestructura real de punta a punta

```java
@Test
void fullFlow_registerLoginCreateAndReadTask_throughRealPostgresAndRedis() throws Exception {
    // registrar, loguear, crear una tarea y leerla — igual que cualquier test de integración
    // ya conocido de fases anteriores, pero esta vez contra Postgres y Redis de verdad.

    var keysResult = redis.execInContainer("redis-cli", "keys", "tasks::*");
    assertThat(keysResult.getStdout()).contains("tasks::" + taskId);
}
```

La primera parte del test no es nueva — es el mismo patrón de MockMvc + JWT real usado desde la Fase 1. Lo nuevo es la última línea: `redis.execInContainer(...)` ejecuta `redis-cli` **dentro** del propio contenedor de Redis, el mismo mecanismo que se usa para inspeccionar Redis a mano en Fase 4 (`docker compose exec redis redis-cli ...`), aquí automatizado como parte del test. Confirma, de forma empírica y no simulada, que la lectura de `GET /tasks/{id}` de verdad dejó una entrada en Redis — no solo que el endpoint respondió `200`.

## `*IT.java` y `maven-failsafe-plugin`

Los tests de esta página usan el sufijo `IT` (`TaskApiIT`, no `TaskApiTest`) a propósito: es la convención que separa Surefire (`./mvnw test`, corre `*Test.java`, rápido, sin Docker) de Failsafe (`./mvnw verify`, corre además `*IT.java`, más lento, necesita Docker real para levantar los contenedores). Separar ambos conjuntos importa porque no todo el mundo que corre `./mvnw test` durante el desarrollo diario tiene Docker disponible o quiere esperar a que arranquen contenedores — Failsafe se reserva para `verify`, el mismo comando que ya usa la integración continua de este proyecto.

Sin Docker instalado, `./mvnw verify -DskipITs` compila y empaqueta el proyecto sin intentar arrancar ningún contenedor — útil para confirmar que el código es válido sin poder ejecutar el test completo. La verificación real (contenedores levantados, test corriendo de punta a punta) solo se puede confirmar donde sí hay Docker: en este proyecto, en GitHub Actions.
```

- [ ] **Step 5: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/06-testing/src/test/java/dev/springbootdocs/examples/tasks/TaskApiIT.java \
        docs-site/docs/06-testing/testcontainers.md
git commit -m "feat: add Testcontainers integration test (Postgres + Redis), add Testcontainers page"
```

---

### Task 6: Página de síntesis, `index.md`, README y CI

**Files:**
- Create: `docs-site/docs/06-testing/cuando-usar-cada-uno.md`
- Modify: `docs-site/docs/06-testing/index.md`
- Create: `examples/06-testing/README.md`
- Modify: `.github/workflows/examples-ci.yml`

**Interfaces:**
- Consumes: nombres de página de Tasks 3-5 (`junit-avanzado`, `mockito-y-tests-unitarios`, `testcontainers`), rutas del API (Task 2).
- Produces: ninguna — última pieza de contenido de la fase.

- [ ] **Step 1: Escribir `docs-site/docs/06-testing/cuando-usar-cada-uno.md`**

```markdown
---
title: Cuándo usar cada uno
sidebar_position: 4
---

# Cuándo usar cada uno

Esta fase mostró tres estilos de test con código real del mismo proyecto. Ninguno reemplaza a los otros dos — cada uno responde una pregunta distinta.

| Estilo | Pregunta que responde | Costo | Ejemplo de esta fase |
|---|---|---|---|
| **Unitario (Mockito)** | ¿Esta lógica, aislada de todo lo demás, hace lo correcto? | Milisegundos, sin Spring, sin base de datos | `TaskServiceImplTest` — ownership sin contexto |
| **Integración (`@SpringBootTest` + H2)** | ¿Las piezas están bien conectadas — seguridad, serialización, consultas? | Segundos, contexto Spring completo, base de datos en memoria | Todos los `*ControllerTest` desde la Fase 1 |
| **Testcontainers** | ¿Funciona de verdad contra la infraestructura real (Postgres, Redis), no una aproximación? | Más lento, necesita Docker | `TaskApiIT` — Postgres y Redis reales |

## Una guía rápida

- Si la clase tiene lógica de negocio no trivial y sus dependencias son fáciles de sustituir (interfaces, sin `final`): **unitario**. Es el más barato y el más específico — cuando falla, casi siempre señala exactamente qué está mal.
- Si lo que hay que confirmar es que Spring conecta todo correctamente — una ruta protegida de verdad rechaza sin token, una consulta JPA devuelve lo que promete, la validación de un DTO se dispara: **integración**. No se puede mockear la configuración misma.
- Si el comportamiento depende de una característica real de la infraestructura que H2/`ConcurrentMapCacheManager` no reproducen fielmente — un tipo de columna específico de Postgres, la serialización real contra Redis, un índice o una restricción que H2 no aplica igual: **Testcontainers**. Es el único de los tres que no aproxima nada.

## Ninguno es gratis

Escribir solo tests unitarios de todo deja huecos: nada confirma que las piezas encajan. Escribir solo tests de integración para todo funciona, pero es lento y, cuando algo falla, hay que investigar entre muchas capas para encontrar la causa. Escribir Testcontainers para todo es correcto pero carísimo — nadie quiere esperar a que arranque un contenedor Docker para probar una validación de tres líneas. La combinación de los tres, cada uno donde responde mejor su pregunta, es lo que da cobertura real sin que la suite de tests se vuelva insoportable de correr.
```

- [ ] **Step 2: Reescribir `docs-site/docs/06-testing/index.md`**

```markdown
---
title: Testing
---

# Testing

Hasta ahora, todo test de este sitio pasaba por un contexto Spring completo: `@SpringBootTest`, MockMvc, una base de datos (H2). Es una herramienta sólida, pero no la única — y usarla para todo tiene un costo que a veces no hace falta pagar. Esta fase enseña cuándo eso es demasiado (un test unitario con Mockito basta y sobra) y cuándo es demasiado poco (Testcontainers, para confirmar contra infraestructura real).

## Contenido

1. [JUnit avanzado](./junit-avanzado)
2. [Mockito y tests unitarios](./mockito-y-tests-unitarios)
3. [Testcontainers](./testcontainers)
4. [Cuándo usar cada uno](./cuando-usar-cada-uno)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/06-testing`](https://github.com/HarolRiosDev/spring-boot-docs/tree/main/examples/06-testing): el mismo API de gestión de tareas con auth JWT y caché de la Fase 4, portado sin cambios de diseño — el foco aquí es cómo se prueba, no qué se prueba.
```

- [ ] **Step 3: Escribir `examples/06-testing/README.md`**

```markdown
# 06-testing

API REST de gestión de tareas — ejemplo ejecutable de la Fase 6 (Testing) del sitio **Spring Boot desde cero**. Mismo dominio y mismo API que `04-cache-redis` (auth JWT, roles, ownership, caché) — el endpoint público es idéntico. El foco de esta fase no es el API sino cómo se prueba: tests parametrizados (JUnit), tests unitarios puros (Mockito) y tests de integración contra infraestructura real (Testcontainers).

## Requisitos

- JDK 21 o superior.
- Docker — necesario para `docker compose up` (ejecutar la app manualmente) **y** para los tests `*IT.java` de Testcontainers (`./mvnw verify`). Ver [Sin Docker](#sin-docker-perfil-h2) si no lo tienes instalado.

## Levantar la base de datos y Redis

```bash
docker compose up -d
```

Levanta PostgreSQL en `localhost:5436` (usuario/contraseña/base de datos: `tasks_testing`) y Redis en `localhost:6379`.

## Ejecutar

```bash
./mvnw spring-boot:run
```

Flyway crea el esquema y siembra un usuario administrador al arrancar:

- **username:** `admin`
- **password:** `admin12345`

## Sin Docker (perfil `h2`)

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

Corre la app contra H2 en archivo (`data/tasks_testing.mv.db`, en `.gitignore`) y fuerza `ConcurrentMapCacheManager` en vez de `RedisCacheManager` — cero Postgres, cero Redis.

## Endpoints

Idénticos a la Fase 4.

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

## Tests

```bash
./mvnw test
```

Tests unitarios (`TaskServiceImplTest`, con Mockito, sin Spring ni base de datos) y de integración con H2 + `spring.cache.type: simple` — no requieren Docker.

```bash
./mvnw verify
```

Añade `TaskApiIT`, que levanta Postgres y Redis reales vía Testcontainers — **requiere Docker corriendo**. Sin Docker, usar `./mvnw verify -DskipITs` para compilar y empaquetar sin ejecutar ese test.
```

- [ ] **Step 4: Añadir `06-testing` a la matriz de CI**

En `.github/workflows/examples-ci.yml`, añadir una línea dentro de `matrix.example`, después de `04-cache-redis`:

```yaml
        example:
          - 00-hello-world
          - 01-fundamentos
          - 02-persistencia
          - 03-security-jwt
          - 04-cache-redis
          - 06-testing
```

- [ ] **Step 5: Commit**

```bash
git add docs-site/docs/06-testing/cuando-usar-cada-uno.md \
        docs-site/docs/06-testing/index.md \
        examples/06-testing/README.md \
        .github/workflows/examples-ci.yml
git commit -m "docs: add synthesis page, rewrite Fase 6 index, add README, add 06-testing to CI matrix"
```

---

### Task 7: Verificación final de la Fase 6

**Files:** ninguno nuevo — solo verificación.

- [ ] **Step 1: Build limpio del sitio**

```bash
cd docs-site
rm -rf build .docusaurus
npm run build
```

Expected: éxito, cero enlaces rotos.

- [ ] **Step 2: Tests limpios del ejemplo (Surefire + empaquetado, sin Docker)**

```bash
cd examples/06-testing
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B clean verify -DskipITs
```

Expected: `BUILD SUCCESS`. Confirmar en el output el conteo total de tests de Surefire (36 heredados de Fase 4, netos -1+5 de la parametrización de Task 3, +4 de `TaskServiceImplTest` de Task 4 = 44), y que `TaskApiIT` aparece como **compilado** (fase `test-compile`) pero no ejecutado (`-DskipITs`).

- [ ] **Step 3: Confirmar el bit de ejecución de `mvnw`**

```bash
cd /d/Proyectos/spring-boot
git ls-files -s examples/06-testing/mvnw
```

Expected: modo `100755`.

- [ ] **Step 4: Validar `docker-compose.yml` de nuevo**

```bash
npx -y js-yaml examples/06-testing/docker-compose.yml
```

Expected: parsea sin errores, dos servicios (`postgres`, `redis`).

- [ ] **Step 5: Verificar las 5 rutas nuevas y el orden del sidebar**

```bash
cd docs-site
npm run serve &
sleep 3
for path in 06-testing 06-testing/junit-avanzado 06-testing/mockito-y-tests-unitarios 06-testing/testcontainers 06-testing/cuando-usar-cada-uno; do
  curl -s -o /dev/null -w "%{http_code} /docs/$path\n" "http://localhost:3000/spring-boot-docs/docs/$path/"
done
kill %1
```

Expected: `200` para las 5 rutas (nota: con `/` final — ver la lección aprendida en la sesión de esta misma fase sobre enlaces internos y `trailingSlash`). Confirmar en `build/` que las 4 páginas aparecen en el sidebar en el orden: JUnit avanzado → Mockito y tests unitarios → Testcontainers → Cuándo usar cada uno.

- [ ] **Step 6: Confirmar que todos los enlaces internos nuevos llevan barra final (lección de esta misma sesión — bug de enlaces 404 ya corregido en `main`)**

```bash
cd /d/Proyectos/spring-boot
grep -rn '\](/docs/[a-zA-Z0-9-]*)' docs-site/docs/06-testing/ examples/06-testing/README.md 2>/dev/null
```

Expected: sin salida (ningún link absoluto a una página índice sin barra final). Los links de esta fase usan rutas relativas (`./junit-avanzado`, etc.), que no tienen este problema — ver Task 3-6.

- [ ] **Step 7: Confirmar CI matrix actualizada**

```bash
grep -A7 "matrix:" .github/workflows/examples-ci.yml
```

Expected: la lista incluye `00-hello-world`, `01-fundamentos`, `02-persistencia`, `03-security-jwt`, `04-cache-redis` y `06-testing`.

- [ ] **Step 8: Documentar los pendientes reales (Docker/Testcontainers) — no simularlos**

Confirmar en el reporte de esta tarea, explícitamente, que:
- Ninguna verificación de este plan ha ejecutado `TaskApiIT` contra Docker real — solo se confirmó que compila (`test-compile`) y que el resto del build pasa con `-DskipITs`.
- La única confirmación real de que `@ServiceConnection` conecta correctamente `PostgreSQLContainer`/`RedisContainer`, y de que `redis.execInContainer("redis-cli", "keys", ...)` funciona como se espera, llega con el primer `./mvnw verify` en GitHub Actions tras el push — **gate explícito antes de dar la fase por cerrada**, documentado en el spec.
- Esto queda como pendiente explícito para el usuario, igual que Docker/Postgres/Redis reales en Fases 2-4.

- [ ] **Step 9: Commit final si queda algo pendiente**

```bash
git status
git add -A
git commit -m "chore: Fase 6 complete — Testing content + JUnit/Mockito/Testcontainers example + CI" --allow-empty
```

---

## Self-Review

**Cobertura del spec:** estructura de contenido (índice + 4 páginas, Tasks 3, 4, 5, 6), ejemplo ejecutable independiente portando Fase 4 verbatim (Tasks 1-2), énfasis pedagógico en Mockito liderando con criterio de decisión y errores comunes antes del código (Task 4, página `mockito-y-tests-unitarios.md`), Testcontainers con `@ServiceConnection` para Postgres y `com.redis:testcontainers-redis` (Task 5), página de síntesis con tabla de decisión (Task 6), CI matrix (Task 6), tratamiento explícito de "sin Docker localmente" en cada paso relevante (Tasks 1, 5, 7), gate de CI real antes de cerrar la fase (Task 7 Step 8) — todos cubiertos. Las lecciones técnicas heredadas listadas en el spec aplicadas: `examples/06-testing` completamente independiente (ningún task toca `04-cache-redis`, solo lo lee para copiar), bit de `mvnw` verificado (Task 1 Step 4), `pom.xml` limpiado (Task 1 Step 5), coordenadas reales de Testcontainers verificadas empíricamente contra Initializr y Maven Central en vez de asumidas (Global Constraints), divergencia test-vs-producción como tema central con mitigación explícita (gate de CI, Task 7 Step 8).

**Placeholders:** ninguno de contenido/código. Los 6 bloques `cp`/bucles de copia de Tasks 1-2 no son placeholders — son instrucciones concretas y verificables (origen y destino exactos, conteo de archivos esperado en el "Expected" de cada step), preferidas a retipear ~35 archivos ya escritos y verificados en Fase 4 (evita drift de transcripción). El nombre exacto de la clase Testcontainers de Redis (`com.redis.testcontainers.RedisContainer`) se verificó inspeccionando el jar real descargado de Maven Central, no se asumió.

**Consistencia de tipos/nombres:** `TaskServiceImpl(TaskRepository, CachedTaskLookup)` con la misma firma en Task 2 (portado) y Task 4 (`TaskServiceImplTest`); `CachedTaskLookup.findById(Long)` lanzando `TaskNotFoundException` consistente entre Task 2 (portado, ya así desde Fase 4) y Task 4; `TaskAccessDeniedException`/`TaskNotFoundException` con los mismos constructores (`Long id`) usados en Task 4; nombres de usuario únicos por test (`wendy`, `xavier`, `yolanda` en Task 3; `itzel` en Task 5) verificados contra los ya usados en `TaskControllerTest`/`AuthControllerTest` para no colisionar en el mismo contexto H2 compartido; puerto Postgres `5436` y db `tasks_testing` consistentes entre `docker-compose.yml`, `application.yml`, `application-h2.yml` y `README.md` (todos Task 1/6); rutas del API (`/auth/register`, `/auth/login`, `/tasks`, `/tasks/{id}`) consistentes entre `TaskApiIT` (Task 5) y `README.md` (Task 6); `JAVA_HOME=/c/jdk-23.0.1` usado consistentemente en todas las invocaciones de `mvnw`; conteo acumulado de tests correcto en cada "Expected" (36 tras Task 2, 40 tras Task 3 [-1+5 neto], 44 tras Task 4, 44 compilados + `TaskApiIT` sin ejecutar en Task 5, 44 confirmados en la verificación final de Task 7).
