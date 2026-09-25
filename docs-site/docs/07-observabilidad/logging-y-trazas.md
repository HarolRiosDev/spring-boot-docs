---
title: Logs y trazas
sidebar_position: 3
---

# Logs y trazas

Las métricas dicen *que* algo va mal: el p95 se ha disparado, han subido los 500. Los logs dicen *qué* pasó exactamente en una petición concreta. Para que sirvan en producción tienen que cumplir tres cosas: decir lo justo, poder filtrarse por petición y poder leerlos una máquina.

## SLF4J en dos minutos

Spring Boot usa SLF4J como API de logging, con Logback por debajo. Cada clase declara su propio logger:

```java
private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);
```

| Nivel | Para qué |
|---|---|
| `ERROR` | Algo falló y alguien tiene que mirarlo |
| `WARN` | Algo raro que no es un fallo pero conviene saber: un acceso denegado, un reintento |
| `INFO` | Hechos de negocio y del ciclo de vida: tarea creada, aplicación arrancada |
| `DEBUG` / `TRACE` | Detalle para depurar; desactivados por defecto |

`TaskServiceImpl` registra la creación y el borrado de tareas (`INFO`) y los intentos de acceder a una tarea ajena (`WARN`):

```java
log.info("Tarea {} creada por {}", saved.getId(), currentUser.getUsername());
log.info("Tarea {} borrada por {}", id, currentUser.getUsername());
log.warn("Acceso denegado: {} intentó acceder a la tarea {}", currentUser.getUsername(), task.getId());
```

Los `{}` son parámetros: SLF4J solo construye el texto final si ese nivel está activado. Con una concatenación (`"Tarea " + id + " creada"`), el `String` se construiría siempre, aunque nadie lo fuera a escribir.

:::caution[Lo que nunca va a un log]
Contraseñas, tokens JWT, hashes de contraseña, números de tarjeta, cuerpos completos de peticiones con datos personales. Un log se copia a muchos sitios (ficheros, plataformas de logs, copias de seguridad) y lo lee mucha más gente que la base de datos. Aquí se registra el username porque sirve para auditar quién hizo qué; en un sistema real, qué datos personales pueden acabar en los logs lo decide también la política de privacidad.
:::

## Un id por petición: `traceId` y `spanId`

Con Micrometer Tracing en el classpath (aquí con Brave por debajo), cada petición HTTP abre una **traza** con un identificador único, el `traceId`. Dentro de la traza, cada tramo de trabajo es un **span**, con su propio `spanId`. En este ejemplo hay un span por petición; en un sistema con varios servicios, la misma traza los atraviesa todos y cada uno añade sus spans.

Spring Boot añade los dos ids a cada línea de log sin configurar nada. En el formato de texto van entre corchetes, `[traceId-spanId]`:

```
2026-09-25T18:59:24.133+02:00  INFO 3736 --- [07-observabilidad] [nio-8080-exec-8] [6ab6a86c9ac3a52b216faf7c468f43fb-b1433ed9649b2a78] d.s.e.tasks.service.TaskServiceImpl      : Tarea 1 creada por admin
```

Todas las líneas que escribe una misma petición llevan el mismo `traceId`: filtrar por él aísla esa petición entre miles.

:::info[El muestreo no quita los ids]
Micrometer Tracing muestrea por defecto el 10 % de las trazas (`management.tracing.sampling.probability`). Ese porcentaje solo decide qué trazas se **envían** a un visor de trazas como Zipkin, Jaeger o Tempo, y este ejemplo no usa ninguno. Los ids aparecen igualmente en **todas** las líneas de log.
:::

## Si la petición ya trae una traza

Cuando la petición llega de un gateway o de otro servicio que ya abrió la traza, viene con la cabecera estándar W3C `traceparent`:

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

La aplicación la lee y **continúa** esa traza en vez de abrir una nueva: sus logs llevan el `traceId` `4bf92f3577b34da6a3ce929d0e0e4736`, el mismo que los del servicio que la llamó. Spring Boot acepta por defecto los formatos W3C y B3.

Como ese id lo puede fijar quien hace la petición, sirve para correlacionar logs, no para demostrar nada: nunca lo uses como dato de seguridad.

## `X-Trace-Id`: el id en la respuesta

El `traceId` está en los logs, pero el cliente no lo ve. El ejemplo lo devuelve en una cabecera de cada respuesta del API (el puerto de gestión, 8081, no la lleva):

```java
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    public TraceIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Span span = tracer.currentSpan();
        if (span != null) {
            response.setHeader(TRACE_ID_HEADER, span.context().traceId());
        }
        filterChain.doFilter(request, response);
    }
}
```

Es el flujo de cualquier soporte: el cliente ve un error y reporta el `X-Trace-Id` de esa respuesta; quien opera la app filtra los logs por ese id y ve exactamente qué pasó en esa petición y en ninguna otra.

El filtro añade la cabecera **antes** de continuar con la cadena, cuando la respuesta aún no se ha empezado a enviar. Y el sitio que ocupa en la cadena de filtros importa:

```java
@Bean
public FilterRegistrationBean<TraceIdFilter> traceIdFilter(Tracer tracer) {
    FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter(tracer));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
}
```

- Tiene que ir **después** del filtro de observación HTTP de Spring Boot, que es el que crea el span (orden `HIGHEST_PRECEDENCE + 1`). Antes de él no hay ningún `traceId` que devolver.
- Tiene que ir **antes** de Spring Security (orden `-100`). Si fuera después, una petición rechazada con 401 o 403 no llegaría al filtro y saldría sin cabecera, justo cuando más se necesita.

Por eso se registra con un `FilterRegistrationBean` y un orden explícito, y no con un `@Component` a secas: Spring Boot lo registraría con el orden por defecto, detrás de Spring Security.

## Logs en JSON

Una línea de texto es cómoda de leer en una terminal e incómoda de procesar para una máquina. Las plataformas de logs (Elasticsearch, Loki, Datadog…) trabajan mejor con JSON: permiten buscar `traceId: "6ab6..."` o "todos los `WARN` de `TaskServiceImpl` de la última hora" sin expresiones regulares. Spring Boot lo trae de serie, y el ejemplo lo activa en el perfil `prod`:

```yaml
logging:
  structured:
    format:
      console: ecs
```

Con `SPRING_PROFILES_ACTIVE=prod`, una línea como la de antes sale así (formateada aquí para leerla; en la consola es una sola línea):

```json
{
  "@timestamp": "2026-09-25T17:11:45.745725800Z",
  "log": { "level": "INFO", "logger": "dev.springbootdocs.examples.tasks.service.TaskServiceImpl" },
  "process": { "pid": 16036, "thread": { "name": "http-nio-8080-exec-7" } },
  "service": { "name": "07-observabilidad", "version": "0.0.1-SNAPSHOT", "node": {} },
  "message": "Tarea 1 creada por admin",
  "traceId": "6ab6ab511eef1ff5a275d8b5891c50c3",
  "spanId": "b5c74e34de0db8fd",
  "ecs": { "version": "8.11" }
}
```

`ecs` es *Elastic Common Schema*, el más extendido de los formatos que soporta Spring Boot; los otros dos son `logstash` y `gelf` (Graylog). El campo `service.version` solo aparece al ejecutar el jar empaquetado, porque sale de su manifiesto; con `./mvnw spring-boot:run` no está.

## Cómo se prueba

- `TraceIdFilterTest` comprueba que cada respuesta lleva un `X-Trace-Id` de 32 caracteres hexadecimales, que dos peticiones tienen ids distintos, que un 401 también lo lleva y que una cabecera `traceparent` entrante se continúa. Esa última comprobación necesita `@AutoConfigureTracing`: en los tests, Spring Boot desactiva la exportación **y la propagación** de trazas. Los ids se siguen generando, pero sin la anotación la cabecera `traceparent` se ignoraría.
- `ProdProfileTest` arranca la app con el perfil `prod`, captura la salida de la consola con `OutputCaptureExtension` y comprueba que la línea que escribe `TaskServiceImpl` al crear una tarea es JSON válido, con `message`, `log.logger` y un `traceId` en el primer nivel.
