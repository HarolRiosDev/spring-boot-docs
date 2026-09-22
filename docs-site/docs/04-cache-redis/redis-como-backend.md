---
title: Redis como backend
sidebar_position: 2
---

# Redis como backend

`@Cacheable`/`@CacheEvict` (ver [Spring Cache básico](./spring-cache-basico)) no cambian según el backend — lo que cambia es la configuración del `CacheManager` que Spring Boot autoconfigura. Con `spring-boot-starter-data-redis` en el classpath y sin forzar otro tipo, Spring Boot elige `RedisCacheManager` automáticamente.

## Redis en `docker-compose.yml`

```yaml
services:
  postgres:
    # ...
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

Sin volumen: Redis aquí es una caché, no una fuente de verdad — perder su contenido en un reinicio del contenedor es aceptable, y de hecho coherente con lo que representa.

## TTL explícito

```java
RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10));
```

Sin `entryTtl`, las entradas de `RedisCacheManager` viven para siempre. Una fase de caché sin expiración estaría incompleta: en producción, algo tiene que forzar eventualmente una relectura desde la base de datos, aunque solo sea el paso del tiempo — sobre todo porque, como se ve en [Invalidación de caché](./invalidacion-de-cache), la invalidación explícita (`@CacheEvict`) solo cubre las escrituras que pasan por *esta* aplicación.

## Por qué JSON y no serialización Java nativa

Por defecto, si no se configura ningún serializador, Spring Data Redis usa `JdkSerializationRedisSerializer` — la serialización binaria estándar de Java, que exige que las clases cacheadas implementen `Serializable` y produce un formato binario opaco. En su lugar, esta fase usa un serializador JSON:

```java
GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(typeValidator)
        .build();

RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(10))
        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
```

JSON es legible directamente con `redis-cli` (útil para depurar), no exige `Serializable` en las entidades, y es más tolerante a cambios de versión de la app que un formato binario ligado a la implementación exacta de la clase Java.

## Un detalle propio de Jackson 3

`GenericJacksonJsonRedisSerializer` cachea valores como `Object` genérico — al leer de Redis, no sabe estáticamente si debe reconstruir un `Task`, un `User` o cualquier otra cosa, así que necesita que el propio JSON lleve el nombre de la clase (`"default typing"`). A diferencia de su predecesor de Jackson 2 (`GenericJackson2JsonRedisSerializer`, ya deprecado), la versión de Jackson 3 **no** activa eso por defecto — hay que pedirlo explícitamente, y con un validador que acote qué clases se pueden reconstruir así:

```java
PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("dev.springbootdocs.examples.tasks.")
        .build();
```

La alternativa más corta, `enableUnsafeDefaultTyping()` (sin validador), acepta reconstruir *cualquier* clase del classpath a partir de ese campo de tipo — el nombre "unsafe" no es decorativo: es la puerta clásica de una deserialización insegura si Redis dejara de ser, algún día, un almacén exclusivo de esta app. Acotar el validador al propio paquete evita ese riesgo sin perder la funcionalidad que hace falta aquí.

## Todo junto: el `@Bean` en `CacheConfig`

Los tres fragmentos anteriores (`typeValidator`, `valueSerializer`, `cacheConfiguration`) no son código suelto — son variables locales de un mismo método, dentro de la misma clase `CacheConfig` que ya vimos en [Spring Cache básico](./spring-cache-basico) con `@EnableCaching`. Esta es la clase completa, con el `@Bean` que faltaba en esa página:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("dev.springbootdocs.examples.tasks.")
                .build();
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
        return builder -> builder.cacheDefaults(cacheConfiguration);
    }
}
```

La línea que de verdad conecta todo es la última: `return builder -> builder.cacheDefaults(cacheConfiguration);`. `RedisCacheManagerBuilderCustomizer` es una interfaz funcional que Spring Boot invoca durante el arranque, pasándole el `RedisCacheManager.RedisCacheManagerBuilder` que está a punto de construir el `CacheManager` autoconfigurado; `cacheDefaults(cacheConfiguration)` le dice a ese builder "usa esta configuración —con su TTL y su serializador JSON— como valor por defecto para cualquier caché declarada con `@Cacheable`". Sin devolver ese lambda, las tres variables locales quedarían construidas pero nunca aplicadas: `RedisCacheManager` seguiría usando sus valores por defecto (sin TTL, con `JdkSerializationRedisSerializer`).

## Un gotcha de nombres, no de diseño

Dos parejas de clases con el mismo nombre simple conviven en el classpath de este proyecto y es fácil importar la equivocada:

- `RedisCacheConfiguration` existe tanto en `org.springframework.boot.autoconfigure.cache` (de Spring Boot, no se usa aquí) como en `org.springframework.data.redis.cache` (de Spring Data Redis, la correcta para `entryTtl`/`serializeValuesWith`).
- `RedisCacheManagerBuilderCustomizer` vive en `org.springframework.boot.cache.autoconfigure` en Spring Boot 4.x — un paquete distinto al de versiones anteriores de Spring Boot.

Ninguno de los dos es un problema de diseño: es puramente que el IDE puede autocompletar el import equivocado si no se presta atención.
