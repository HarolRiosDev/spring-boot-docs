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

Los fragmentos anteriores (`typeValidator`, `valueSerializer` y la `RedisCacheConfiguration`) no son código suelto: son las piezas de un mismo método, dentro de la clase `CacheConfig` que ya vimos en [Spring Cache básico](./spring-cache-basico) con `@EnableCaching`. Esta es la clase completa, con el `@Bean` que faltaba en esa página:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    private static final String ENTITY_BASE_PACKAGE = "dev.springbootdocs.examples.tasks.";

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(ENTITY_BASE_PACKAGE)
                .build();
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }
}
```

Al arrancar, Spring Boot busca un bean de tipo `RedisCacheConfiguration` y, si lo encuentra, lo usa como configuración por defecto de cada caché de Redis que crea, incluida la de `@Cacheable(value = "tasks")`. Sin ese bean, `RedisCacheManager` usaría la suya: sin TTL y con `JdkSerializationRedisSerializer`.

`RedisCacheConfiguration` es inmutable: `entryTtl(...)` y `serializeValuesWith(...)` no modifican el objeto, devuelven una copia nueva con ese cambio. Por eso las llamadas van encadenadas y el método devuelve el resultado final.

`RedisCacheConfigurationTest` lo comprueba sin necesitar Redis: pide la caché `tasks` al `CacheManager` y verifica que caduca a los 10 minutos y que sabe serializar un `Task`.

:::note[La otra forma que verás en tutoriales]
Muchos tutoriales consiguen lo mismo con un `RedisCacheManagerBuilderCustomizer`, que recibe el constructor del `CacheManager` y llama a `builder.cacheDefaults(...)`. Funciona, pero su sitio es otro: configurar cachés concretas una a una (un TTL distinto para cada una, por ejemplo). Y tiene una trampa: las cachés que declares en `spring.cache.cache-names` se crean antes de que se ejecute el customizer, así que se quedan sin esa configuración. En la página de [métricas de la Fase 7](/docs/07-observabilidad/metricas) se ve un caso real. Para cambiar la configuración por defecto, el bean `RedisCacheConfiguration` es la forma directa.
:::

## Un gotcha de nombres, no de diseño

`RedisCacheConfiguration` existe dos veces con el mismo nombre simple. La que se usa aquí es la de Spring Data Redis, `org.springframework.data.redis.cache.RedisCacheConfiguration`, la que tiene `entryTtl` y `serializeValuesWith`. Spring Boot tiene otra en `org.springframework.boot.cache.autoconfigure`: es una clase interna de su autoconfiguración y no es pública, así que un import que apunte ahí no compila.
