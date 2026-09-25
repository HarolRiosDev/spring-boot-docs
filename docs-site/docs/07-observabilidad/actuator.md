---
title: Actuator
sidebar_position: 1
---

# Actuator

`spring-boot-starter-actuator` añade a la aplicación una serie de endpoints bajo `/actuator` que no forman parte del API de negocio: sirven para preguntarle a la aplicación cómo está. Son los que consulta la plataforma donde corre (Kubernetes, un balanceador, un servicio gestionado) y los que recoge Prometheus.

## Los endpoints que importan

| Endpoint | Qué responde |
|---|---|
| `/actuator/health` | Si la app está bien (`UP`) o no (`DOWN`) y, opcionalmente, el estado de cada dependencia: base de datos, disco… |
| `/actuator/health/liveness` y `/actuator/health/readiness` | Las dos preguntas de un orquestador: ¿está viva? ¿puede recibir tráfico? |
| `/actuator/info` | Qué versión está desplegada |
| `/actuator/metrics` | Las métricas de la app, navegables una a una |
| `/actuator/prometheus` | Las mismas métricas, en el formato de texto que lee Prometheus |

Actuator trae muchos más (`env`, `beans`, `loggers`, `heapdump`…). Algunos son peligrosos si quedan abiertos: `heapdump` descarga un volcado de la memoria del proceso, con todo lo que hubiera en ella en ese momento, tokens y contraseñas incluidos.

## Qué exponer

Por HTTP, Spring Boot solo expone `health` por defecto. El resto hay que pedirlo uno a uno:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
```

Una lista cerrada es más segura que `include: "*"`: si una versión futura de Spring Boot añade un endpoint, no queda expuesto sin que nadie lo decida.

## Un puerto solo para gestión

```yaml
management:
  server:
    port: 8081
```

Con esta propiedad, Spring Boot arranca un segundo servidor web: la API sigue en el 8080 y Actuator pasa al 8081. En producción solo se publica el 8080 (en el balanceador, el firewall o el `Service` de Kubernetes). Al 8081 solo llegan Prometheus y los chequeos de la propia plataforma, desde dentro de la red.

Esa separación es la que permite dejar `health` y `prometheus` sin autenticación: no son públicos, porque nadie de fuera puede llegar a ese puerto. Y en el 8080 ya no hay Actuator: `http://localhost:8080/actuator/health` responde 401, como cualquier otra ruta que no es pública.

## Actuator y Spring Security

La `SecurityFilterChain` de la aplicación se aplica también en el puerto de gestión. Para escribir reglas sobre los endpoints de Actuator, Spring Boot ofrece `EndpointRequest`:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(EndpointRequest.to("health", "info", "prometheus")).permitAll()
        .requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole("ADMIN")
        .requestMatchers("/auth/**").permitAll()
        // ...
        .anyRequest().authenticated())
```

`EndpointRequest` identifica cada endpoint por su id (`health`, `prometheus`…), así que las reglas siguen valiendo aunque cambien el puerto o la ruta base. El orden importa: gana la primera regla que coincide, así que la específica (`to(...)`) va antes que la general (`toAnyEndpoint()`). Con estas reglas, `/actuator/metrics` responde 401 sin token, 403 con el token de un `USER` y 200 con el de un `ADMIN`.

## Health: cuánto detalle y para quién

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
      roles: ADMIN
```

Una petición anónima solo ve el estado global:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

Con el token de un `ADMIN`, cada componente con su estado (recortado):

```json
{
  "components": {
    "db": { "status": "UP", "details": { "database": "H2", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP", "details": { "total": 498972913664, "free": 110898253824, "threshold": 10485760 } },
    "livenessState": { "status": "UP" },
    "ping": { "status": "UP" },
    "readinessState": { "status": "UP" }
  },
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```

Es la salida con H2. Con Postgres, `database` dice `PostgreSQL` y aparece también el componente `redis`.

Estos detalles dicen qué base de datos usas y cómo es tu infraestructura: es información útil para quien opera la aplicación y también para quien quiere atacarla. En el perfil `prod` del ejemplo, `show-details: never` los oculta a todos.

:::caution[Sin Redis, `health` da `DOWN`]
Spring Boot añade un health check por cada dependencia que detecta. Si Redis no está corriendo, el suyo da `DOWN` y `/actuator/health` responde **503**, aunque la app funcione. El perfil `h2` y el de los tests lo desactivan con `management.health.redis.enabled: false`, porque en ellos no hay Redis y la caché vive en memoria.
:::

## Liveness y readiness

Un orquestador como Kubernetes hace dos preguntas distintas, y es importante no mezclarlas:

- **Liveness: ¿reinicio el proceso?** Solo debe dar `DOWN` si la aplicación está rota de una forma que solo arregla un reinicio.
- **Readiness: ¿le mando tráfico?** Puede dar `DOWN` un rato (mientras arranca, o sin una dependencia imprescindible) sin que haya que matar nada.

Si la base de datos se cae, la aplicación no puede atender peticiones, pero reiniciarla no arregla la base de datos. Peor: si la base de datos estuviera en liveness, el orquestador reiniciaría todas las instancias a la vez, una y otra vez. Por eso la base de datos va en readiness y no en liveness:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState, db
```

`probes.enabled: true` publica `/actuator/health/liveness` y `/actuator/health/readiness`; Spring Boot solo las activa por su cuenta cuando detecta que corre en Kubernetes. Con el token de un `ADMIN`, readiness muestra el componente `db` y liveness no.

## Info: qué versión está corriendo

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>build-info</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

El goal `build-info` genera `META-INF/build-info.properties` al compilar, y `/actuator/info` lo muestra:

```json
{"build":{"artifact":"tasks-observability","name":"TasksObservability","time":"2026-09-25T16:58:58.478Z","version":"0.0.1-SNAPSHOT","group":"dev.springbootdocs.examples"}}
```

Cuando algo falla en producción, lo primero es saber qué versión está desplegada de verdad.

## Cómo se prueba

`ActuatorEndpointsTest` arranca la aplicación con servidores reales y el puerto de gestión separado, como en producción:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@AutoConfigureMetrics
class ActuatorEndpointsTest {

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;
    // ...
}
```

`management.server.port=0` pide un puerto libre cualquiera, y `@LocalManagementPort` lo inyecta en el test. Los tests comprueban cada regla de esta página: `health` público y sin detalles, detalles solo para `ADMIN`, `db` en readiness y no en liveness, `metrics` solo para `ADMIN`, `info` con la versión y Actuator ausente en el puerto de la API.

`@AutoConfigureMetrics` no es decorativa. En los tests, Spring Boot desactiva la exportación de métricas, y sin esa anotación `/actuator/prometheus` no existe. En el puerto de gestión el síntoma es un 401, porque `EndpointRequest.to("prometheus")` no reconoce un endpoint que no está y la petición cae en `anyRequest().authenticated()`.
