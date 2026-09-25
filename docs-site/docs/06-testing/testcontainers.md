---
title: Testcontainers
sidebar_position: 3
---

# Testcontainers

Las Fases 2-4 solo pudieron probar contra H2 (en vez de Postgres) y `ConcurrentMapCacheManager` (en vez de Redis) : era la forma de tener base de datos y caché en un test automatizado sin exigir que quien lo ejecute tenga Docker. Testcontainers cierra esa brecha: levanta contenedores Docker reales, uno por dependencia, solo durante la ejecución de los tests, y los destruye al terminar.

## `@Testcontainers` + `@Container` + `@ServiceConnection`

```java
@Testcontainers
@SpringBootTest(properties = "spring.cache.type=redis")
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

### La trampa del `application.yml` de test compartido

`src/test/resources/application.yml` fija `spring.cache.type: simple` — correcto para los tests de Mockito/`@SpringBootTest` con H2 de las páginas anteriores, que **no** deben depender de un Redis real corriendo. El problema es que `TaskApiIT` también está en el classpath de test y, sin más, hereda ese mismo `application.yml`: `@Cacheable` seguiría escribiendo en el `ConcurrentMapCacheManager` en memoria de Spring, no en el contenedor Redis real que `@Container`/`@ServiceConnection` acaban de levantar — el test pasaría hasta el `andExpect(status().isOk())` y fallaría solo en la última aserción, la que lee la clave directamente de Redis. Por eso `@SpringBootTest(properties = "spring.cache.type=redis")` sobreescribe esa única propiedad, y solo para el `ApplicationContext` de esta clase — los demás tests siguen usando `simple` sin tocarlos. Si estás copiando este patrón a un proyecto propio que también siguió el de la Fase 4 (perfil de test con caché `simple`), vale la pena revisar si tu propio `*IT.java` necesita el mismo override: es un fallo silencioso, no una excepción, así que es fácil no notarlo hasta que se lee el resultado con atención.

## Un test, infraestructura real de punta a punta

```java
@Test
void fullFlow_registerLoginCreateAndReadTask_throughRealPostgresAndRedis() throws Exception {
    // registrar, loguear, crear una tarea y leerla — igual que cualquier test de integración
    // ya conocido de fases anteriores, pero esta vez contra Postgres y Redis de verdad.

    String cacheKey = "tasks::" + taskId;
    var keysResult = redis.execInContainer("redis-cli", "keys", "tasks::*");
    assertThat(keysResult.getStdout().lines()).contains(cacheKey);

    // update (PUT) dispara @CacheEvict — confirmar que la clave desaparece de Redis de verdad.
    mockMvc.perform(put("/tasks/{id}", taskId).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(/* TaskRequest actualizado */ "..."))
            .andExpect(status().isOk());

    var keysAfterUpdateResult = redis.execInContainer("redis-cli", "keys", "tasks::*");
    assertThat(keysAfterUpdateResult.getStdout().lines()).doesNotContain(cacheKey);
}
```

La primera parte del test no es nueva: es el mismo patrón de MockMvc (desde la Fase 1) con un JWT real (desde la Fase 3). Lo nuevo son las líneas con `redis.execInContainer(...)`: ejecutan `redis-cli` **dentro** del propio contenedor de Redis, el mismo mecanismo que se usa para inspeccionar Redis a mano en Fase 4 (`docker compose exec redis redis-cli ...`), aquí automatizado como parte del test. La primera confirma, de forma empírica y no simulada, que la lectura de `GET /tasks/{id}` de verdad dejó una entrada en Redis — no solo que el endpoint respondió `200`. La segunda hace lo simétrico con la escritura: un `PUT /tasks/{id}` dispara `@CacheEvict(value = "tasks", key = "#id")` en `TaskServiceImpl.update`, y la aserción confirma que la clave realmente desapareció de Redis — si se quitara el `@CacheEvict`, la clave seguiría ahí y el test fallaría. Nótese también `.lines()` en vez de comparar el `String` crudo con `contains(...)`: como `redis-cli keys` puede devolver varias claves, comparar contra el texto completo haría que `"tasks::1"` diera un falso positivo si existiera `"tasks::10"`; comparando línea por línea se exige una coincidencia exacta.

## `*IT.java` y `maven-failsafe-plugin`

Los tests de esta página usan el sufijo `IT` (`TaskApiIT`, no `TaskApiTest`) a propósito: es la convención que separa Surefire (`./mvnw test`, corre `*Test.java`, rápido, sin Docker) de Failsafe (`./mvnw verify`, corre además `*IT.java`, más lento, necesita Docker real para levantar los contenedores). Separar ambos conjuntos importa porque no todo el mundo que corre `./mvnw test` durante el desarrollo diario tiene Docker disponible o quiere esperar a que arranquen contenedores — Failsafe se reserva para `verify`, el mismo comando que ya usa la integración continua de este proyecto.

Sin Docker instalado, `./mvnw verify -DskipITs` compila y empaqueta el proyecto sin intentar arrancar ningún contenedor — útil para confirmar que el código es válido sin poder ejecutar el test completo. La verificación real (contenedores levantados, test corriendo de punta a punta) solo se puede confirmar donde sí hay Docker: en este proyecto, en GitHub Actions.

Vale la pena conocer también el patrón de "singleton container": reutilizar la(s) misma(s) instancia(s) de contenedor entre varias clases de test (en vez de que cada clase `@Testcontainers` levante los suyos) para no pagar el costo de arranque de Docker una y otra vez. No se implementa en este proyecto — un solo `*IT.java` no lo justifica — pero es la primera optimización a considerar si esta fase creciera y el tiempo de `verify` empezara a doler.
