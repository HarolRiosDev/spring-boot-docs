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
