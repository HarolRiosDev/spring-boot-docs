---
title: ¿Está lista para producción?
sidebar_position: 5
---

# ¿Está lista para producción?

Una lista para repasar antes de desplegar una API Spring Boot. Cada bloque enlaza a donde se explica.

## Salud

→ [Actuator](./actuator)

- [ ] `/actuator/health` responde y separa **liveness** (¿reinicio?) de **readiness** (¿le mando tráfico?).
- [ ] Readiness incluye las dependencias sin las que no se puede atender (la base de datos); liveness no incluye ninguna externa.
- [ ] El detalle de `health` solo lo ve un `ADMIN`, o nadie (`show-details: never`).
- [ ] El health check de una dependencia que no existe en ese entorno no arrastra el estado global a `DOWN`.
- [ ] `/actuator/info` dice qué versión está desplegada.

## Métricas

→ [Métricas con Micrometer y Prometheus](./metricas)

- [ ] Se publican las métricas técnicas (peticiones, latencia, JVM, pool de conexiones) y Prometheus las recoge.
- [ ] Hay al menos una métrica de negocio que diga si la aplicación hace su trabajo.
- [ ] La latencia se mira con percentiles (p95), no con medias.
- [ ] Ninguna etiqueta usa ids, emails ni texto libre.
- [ ] Ningún nombre de métrica termina en un sufijo reservado de Prometheus (`_created`, `_total`, `_bucket`, `_info`).
- [ ] Cada panel del dashboard consulta una métrica que existe, y lo comprueba un test.

## Logs y trazas

→ [Logs y trazas](./logging-y-trazas)

- [ ] Logs con SLF4J y parámetros (`{}`), cada uno en el nivel que le corresponde.
- [ ] Ni contraseñas, ni tokens, ni hashes, ni datos personales que no deban estar.
- [ ] En producción, logs en JSON.
- [ ] Cada línea lleva el `traceId` de su petición, y cada respuesta del API lo devuelve en `X-Trace-Id`, también las de error.

## Superficie expuesta

→ [Actuator](./actuator) y [OpenAPI con springdoc](./openapi)

- [ ] Actuator vive en un puerto de gestión que no se publica hacia fuera.
- [ ] Solo `health`, `info` y `prometheus` quedan sin autenticación, y solo en ese puerto. Nada de `heapdump`, `env` ni `include: "*"`.
- [ ] Swagger UI apagado en producción.
- [ ] `/error` permitida en Spring Security, para que un 404 o un 500 no lleguen al cliente como 401.

## Lo que ya se vio en fases anteriores

- [ ] El secreto de firma de los JWT llega por variable de entorno, nunca en el repositorio. → [Autenticación con JWT](/docs/03-security-jwt/autenticacion-jwt)
- [ ] `spring.jpa.open-in-view: false`, y el mapeo a DTO dentro de la transacción del servicio. → [Spring Data JPA](/docs/02-persistencia/spring-data-jpa#open-in-view-false)
- [ ] El esquema lo versiona Flyway, y Hibernate solo lo valida (`ddl-auto: validate`). → [Flyway](/docs/02-persistencia/flyway) y [Probar con datos reales](/docs/02-persistencia/probar-con-datos-reales#ddl-auto-validate)
- [ ] Los mensajes que no se pueden procesar van a una dead-letter queue: ni se pierden ni bloquean al consumidor. → [Consumidores](/docs/05-kafka/consumidores#la-dead-letter-queue)
- [ ] Hay tests contra la infraestructura real, no solo contra H2 y cachés en memoria. → [Testcontainers](/docs/06-testing/testcontainers)

## Lo que queda fuera de este recorrido

- **Empaquetar la aplicación en una imagen de contenedor** (con un `Dockerfile`, o con `./mvnw spring-boot:build-image` y buildpacks) y desplegarla.
- **Un visor de trazas** (Zipkin, Jaeger, Tempo), para ver el recorrido completo de una petición a través de varios servicios.
- **Alertas**: que Prometheus (con Alertmanager) o Grafana avisen cuando el p95 o los 5xx pasen de un umbral, en vez de esperar a que alguien mire el dashboard.
- **Centralizar los logs** de todas las instancias en un solo sitio (Loki, la pila ELK), donde buscar por `traceId`.
