---
title: Métricas con Micrometer y Prometheus
sidebar_position: 2
---

# Métricas con Micrometer y Prometheus

Un log cuenta qué pasó en una petición concreta. Una métrica cuenta cuántas veces pasa algo y cuánto tarda, agregado en el tiempo: peticiones por segundo, latencia, errores, memoria. Es lo que se mira para saber si la aplicación va bien *ahora*, y lo que dispara una alerta antes de que un usuario se queje.

## Micrometer: la fachada

Spring Boot mide con **Micrometer**, que es a las métricas lo que SLF4J a los logs: una API común, con un "registro" distinto para cada sistema de monitorización. Con la dependencia `micrometer-registry-prometheus` y el endpoint `prometheus` expuesto (ver [Actuator](./actuator)), todas las métricas se publican en `/actuator/prometheus`, en el formato de texto que lee Prometheus.

## Las métricas que salen solas

Sin escribir una línea de código, la aplicación ya publica, entre otras:

| Métrica (en Prometheus) | Qué mide |
|---|---|
| `http_server_requests_seconds_*` | Cada petición HTTP: cuántas, cuánto tardaron, con qué resultado |
| `jvm_memory_used_bytes` | Memoria de la JVM, por zona (`heap`, `nonheap`) |
| `hikaricp_connections_active` | Conexiones del pool de base de datos en uso |
| `cache_gets_total` | Aciertos y fallos de la caché (con Redis; ver más abajo) |
| `logback_events_total` | Líneas de log emitidas, por nivel |

Así se ve una línea real de `/actuator/prometheus` después de crear una tarea:

```
http_server_requests_seconds_count{error="none",exception="none",method="POST",outcome="SUCCESS",status="201",uri="/tasks"} 1
```

Cada combinación de etiquetas (`method`, `status`, `uri`…) es una **serie temporal** distinta. Fíjate en que `uri` es la plantilla de la ruta: una petición a `/tasks/42` cuenta como `uri="/tasks/{id}"`, no como `/tasks/42`. El motivo está más abajo, en *Cardinalidad*.

## Nombres: Micrometer y Prometheus

En el código, Micrometer usa nombres con puntos (`http.server.requests`). Al publicarlos, el registro de Prometheus los traduce a su convención: guiones bajos, la unidad en el nombre y un sufijo según el tipo. Un *timer* como `http.server.requests` se convierte en varias series (`http_server_requests_seconds_count`, `_sum`, `_max` y, si se activan los histogramas, `_bucket`), y un contador `tasks.creations` en `tasks_creations_total`.

:::caution[Por qué el contador no se llama `tasks.created`]
Es el primer nombre que se le ocurre a cualquiera. Pero el formato de Prometheus (OpenMetrics) reserva el sufijo `_created`, y el cliente de Prometheus lo **quita sin avisar** antes de añadir `_total`: un contador `tasks.created` se publica como `tasks_total`. Un panel de Grafana que buscara `tasks_created_total` se quedaría vacío, sin ningún error en ningún sitio. Los sufijos reservados son `_total`, `_created`, `_bucket` e `_info`. El test que compara el dashboard con las métricas reales, descrito al final de esta página, detecta justo este tipo de error.
:::

## Una métrica de negocio

Las métricas técnicas dicen si la aplicación responde. Las de negocio dicen si hace lo que tiene que hacer: si de repente se crean cero tareas por minuto, algo va mal aunque todas las peticiones devuelvan 200.

```java
public TaskServiceImpl(
        TaskRepository taskRepository, CachedTaskLookup cachedTaskLookup, MeterRegistry meterRegistry) {
    this.taskRepository = taskRepository;
    this.cachedTaskLookup = cachedTaskLookup;
    this.taskCreations = Counter.builder("tasks.creations")
            .description("Tareas creadas desde que arrancó la aplicación")
            .register(meterRegistry);
}

@Override
@Transactional
public TaskResponse create(TaskRequest request, User currentUser) {
    Task task = new Task(request.titulo(), request.descripcion(), request.completada(), currentUser);
    Task saved = taskRepository.save(task);
    taskCreations.increment();
    // ...
}
```

El contador se registra en el constructor, al arrancar, y no la primera vez que se usa. Así la serie existe desde el principio con valor 0, y Grafana distingue "no se ha creado ninguna tarea" de "no hay datos".

Un detalle honesto: se incrementa dentro de la transacción, después del `save`. Si el commit fallara justo después, el contador habría contado una tarea que nunca llegó a existir. Es el mismo problema de "efecto hacia fuera antes del commit" que apareció con la caché en la Fase 4 y con Kafka en la Fase 5. Allí importaba; en una métrica, un error de uno entre millones es aceptable.

## Percentiles: el p95

La media de latencia engaña: si 95 peticiones tardan 10 ms y 5 tardan 2 segundos, la media sale 110 ms y no describe a nadie. Lo que se mira es el **percentil 95** (p95): el tiempo por debajo del cual queda el 95 % de las peticiones. Dicho de otra forma, lo que sufre el 5 % más lento.

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

Con esta propiedad, Micrometer publica además los *buckets* de un histograma (`http_server_requests_seconds_bucket`, con una etiqueta `le` por cada tramo), y Prometheus calcula con ellos el percentil que se le pida. La ventaja frente a calcular el p95 dentro de la aplicación es que los buckets de varias instancias se pueden sumar: el p95 del servicio entero sale bien aunque haya diez copias de la app.

## Cardinalidad

Cada valor distinto de una etiqueta crea una serie temporal nueva, que ocupa memoria en la aplicación y en Prometheus. Por eso:

```java
// ✗ nunca: una serie por usuario, y otra más por cada usuario nuevo
Counter.builder("tasks.creations").tag("user", currentUser.getUsername()).register(meterRegistry);
```

Nunca uses como etiqueta un id de usuario, un id de tarea, un email o cualquier texto libre. Las etiquetas son para valores de un conjunto pequeño y cerrado: método HTTP, código de estado, tipo de evento. Es la misma razón por la que `uri` guarda la plantilla `/tasks/{id}` y no la ruta real.

## Métricas de la caché

Con Redis, `RedisCacheManager` publica `cache_gets_total` (aciertos y fallos), `cache_puts_total` y otras, pero necesita dos propiedades:

```yaml
spring:
  cache:
    cache-names: tasks
    redis:
      enable-statistics: true
```

- Sin `cache-names`, la caché `tasks` se crea la primera vez que se usa, y Spring Boot solo registra las métricas de las cachés que existen al arrancar: las series no aparecen nunca.
- Sin `enable-statistics`, las series existen pero siempre valen 0. El panel de Grafana se queda plano, y parece que la caché no se usa.

:::caution[`cache-names` y la configuración de la caché]
Con `cache-names`, Spring Boot crea la caché `tasks` al arrancar, con la configuración por defecto que haya en ese momento. En la Fase 4, el TTL de 10 minutos y el serializador JSON se aplicaban con un `RedisCacheManagerBuilderCustomizer` que llamaba a `cacheDefaults(...)`; ese customizer se ejecuta **después** de crear las cachés de `cache-names`, así que ya no llega a `tasks`. La caché se quedaría sin TTL y con el serializador de Java, que no sabe guardar un `Task`: cada `GET /tasks/{id}` contra Redis daría 500. Por eso este ejemplo declara la configuración como un bean `RedisCacheConfiguration`, que Spring Boot usa como valor por defecto antes de crear ninguna caché:

```java
@Bean
public RedisCacheConfiguration redisCacheConfiguration() {
    // typeValidator y valueSerializer, igual que en la Fase 4
    return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
}
```

`RedisCacheConfigurationTest` lo comprueba sin Redis: pide la caché `tasks` al `CacheManager` y verifica su TTL y que sabe serializar un `Task`.
:::

La caché en memoria que usan el perfil `h2` y los tests (`ConcurrentMapCacheManager`) no publica métricas: sin Redis, ese panel del dashboard se queda vacío.

## Prometheus y Grafana

El `docker-compose.yml` del ejemplo levanta los dos, además de Postgres y Redis.

**Prometheus** pregunta a la aplicación cada 5 segundos (*scrape*) y guarda cada valor con su marca de tiempo:

```yaml
global:
  scrape_interval: 5s

scrape_configs:
  - job_name: tasks-api
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:8081"]
```

Es Prometheus quien va a buscar las métricas (modelo *pull*), así que le basta con llegar al puerto de gestión. La app corre fuera de Docker, con `./mvnw spring-boot:run`, y el contenedor la alcanza a través de `host.docker.internal`. En `http://localhost:9090/targets` se ve si el scrape funciona: el target `tasks-api` debe aparecer `UP`.

**Grafana** (`http://localhost:3001`, sin login y en solo lectura) arranca con el origen de datos de Prometheus y el dashboard "Tasks API" ya configurados. Se cargan desde archivos del proyecto (*provisioning*), no a mano desde la interfaz:

| Panel | Consulta PromQL |
|---|---|
| Peticiones por segundo (por endpoint) | `sum by (uri) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))` |
| Latencia p95 (por endpoint) | `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[5m])))` |
| Respuestas de error (4xx y 5xx) | `sum by (status) (rate(http_server_requests_seconds_count{status=~"4..\|5.."}[1m]))` |
| Tareas creadas desde el arranque | `sum(tasks_creations_total)` |
| Caché de tareas: aciertos y fallos por segundo | `sum by (result) (rate(cache_gets_total{cache="tasks"}[1m]))` |
| Memoria heap de la JVM | `sum(jvm_memory_used_bytes{area="heap"})` |
| Conexiones activas a la base de datos (Hikari) | `sum(hikaricp_connections_active)` |

Las dos primeras, leídas de dentro afuera:

- `rate(http_server_requests_seconds_count[1m])`: el contador de peticiones solo crece; `rate` lo convierte en "peticiones por segundo" durante el último minuto. `sum by (uri)` junta las series de cada endpoint (todos los métodos, todos los códigos de estado) y deja una línea por `uri`. El filtro `uri!~"/actuator.*"` deja fuera las peticiones a Actuator cuando comparte puerto con el API, como en los tests. Con el puerto de gestión separado del ejemplo, las peticiones al 8081 (incluidos los scrapes de Prometheus) ni siquiera se miden: el filtro no cambia nada, pero el panel sigue siendo correcto si algún día Actuator vuelve al puerto del API.
- `histogram_quantile(0.95, ...)`: toma a qué velocidad se llena cada bucket del histograma en los últimos 5 minutos, suma los de todas las instancias (`sum by (le, uri)`) y calcula el tiempo por debajo del cual queda el 95 % de las peticiones.

## Cómo se prueba sin levantar Grafana

El CI no arranca Prometheus ni Grafana, así que el dashboard no se puede probar mirándolo. Lo que se prueba es lo que más fácilmente falla sin avisar: que cada métrica que consulta el dashboard existe en la aplicación.

`GrafanaDashboardMetricsTest` lee `tasks-api.json`, extrae los nombres de métrica de cada consulta PromQL, hace una petición (las series `http_server_requests_*` no existen hasta la primera) y comprueba que cada nombre aparece en `/actuator/prometheus`. Un nombre mal escrito, un sufijo reservado o un histograma sin activar hacen fallar el test en vez de dejar un panel vacío. Arranca con la caché de Redis de producción (`spring.cache.type=redis`), porque la caché en memoria no publica métricas; no necesita un Redis corriendo, porque `RedisCacheManager` no conecta hasta la primera lectura y el test no lee de la caché.

Los otros dos tests de métricas:

- `TaskMetricsTest` lee el contador directamente del `MeterRegistry`: crear una tarea lo incrementa, y una petición rechazada por validación no.
- `TaskApiIT` (Testcontainers, en CI) lee dos veces la misma tarea contra un Redis real y comprueba que `cache_gets_total{result="hit"}` llega al menos a 1. Es la única forma de probar `enable-statistics`.
