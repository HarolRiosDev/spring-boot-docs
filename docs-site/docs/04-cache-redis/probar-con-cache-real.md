---
title: Probar con caché real
sidebar_position: 4
---

# Probar con caché real

## Sin Docker: `spring.cache.type: simple`

```yaml
spring:
  cache:
    type: simple
```

Fuerza `ConcurrentMapCacheManager`, el `CacheManager` que Spring Boot autoconfigura cuando no hay (o no se quiere usar) un backend externo: un `Map` en memoria por caché, sin serialización de por medio. Es lo que usan tanto el perfil de test (`src/test/resources/application.yml`) como el perfil `h2` (`application-h2.yml`) — mismas anotaciones `@Cacheable`/`@CacheEvict` de producción, funcionando contra una caché real, no un mock, sin necesitar Redis.

## Confirmar la caché contra el `CacheManager` real, no contra un mock

```java
@Autowired
private CacheManager cacheManager;

private Cache tasksCache() {
    Cache cache = cacheManager.getCache("tasks");
    assertThat(cache).isNotNull();
    return cache;
}

@Test
void getTaskById_populatesCache() throws Exception {
    String token = registerAndLogin("cara");
    Long id = createTask(token, "Tarea de cara");
    assertThat(tasksCache().get(id)).isNull();

    mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

    assertThat(tasksCache().get(id)).isNotNull();
}
```

Inyectar el `CacheManager` y preguntarle directamente por la entrada (`cacheManager.getCache("tasks").get(id)`) prueba el comportamiento real de la abstracción de caché — nada de esto pasaría si `@Cacheable` estuviera silenciosamente desactivado por la trampa de auto-invocación descrita en [Spring Cache básico](./spring-cache-basico); el test fallaría de inmediato porque la entrada nunca aparecería.

El mismo enfoque confirma la invalidación:

```java
@Test
void updateTask_evictsCacheEntry() throws Exception {
    // ... crear la tarea, leerla (puebla la caché) ...
    assertThat(tasksCache().get(id)).isNotNull();

    // ... PUT /tasks/{id} ...

    assertThat(tasksCache().get(id)).isNull();
}
```

Y, el test más importante de esta fase, que un acierto de caché nunca se sirve a quien no tiene permiso (ver [Invalidación de caché](./invalidacion-de-cache)):

```java
@Test
void cacheHit_stillEnforcesOwnership_forDifferentUser() throws Exception {
    // primera lectura del dueño: cache miss
    mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk());

    // segunda lectura del MISMO id: acierto de caché, pero de alguien sin permiso
    mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden());
}
```

## Verificación manual contra Redis real

Con Docker disponible y `docker compose up -d` corriendo, desde la carpeta del ejemplo (`redis-cli` se ejecuta dentro del contenedor, no hace falta instalarlo):

```bash
docker compose exec redis redis-cli keys "tasks::*"
docker compose exec redis redis-cli get "tasks::1"
```

Tras un `GET /tasks/1`, debería aparecer una clave `tasks::1` con el JSON de la tarea (gracias a `GenericJacksonJsonRedisSerializer`, ver [Redis como backend](./redis-como-backend)); tras un `PUT`/`DELETE` sobre esa misma tarea, la clave desaparece. El JSON que aparece ahí incluye el `user` de la tarea, pero **no** su contraseña: el getter lleva `@JsonIgnore` precisamente porque este Redis de ejemplo corre sin autenticación y nada aguas abajo necesita el hash. Que ese `user` sea un `User` real y no un proxy perezoso es lo que garantiza el `join fetch` de `findByIdWithUser` (ver [Spring Cache básico](./spring-cache-basico)); ambas cosas están cubiertas por un test unitario de serialización (`CacheValueSerializationTest`) que ejercita el mismo serializador sin necesitar Redis.

Esta misma comprobación —que la clave aparece tras un `GET` y desaparece tras un `PUT`— también existe como test automático contra un Redis real: `TaskApiIT`, en la Fase 6, levanta Redis con Testcontainers y ejecuta `redis-cli` dentro del contenedor (ver [Testcontainers](/docs/06-testing/testcontainers)).
