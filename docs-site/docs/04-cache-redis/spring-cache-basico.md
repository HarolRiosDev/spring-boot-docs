---
title: Spring Cache básico
sidebar_position: 1
---

# Spring Cache básico

Spring Cache es una **abstracción**: un conjunto de anotaciones (`@Cacheable`, `@CacheEvict`) que declaran "este método se puede cachear" sin que el código de negocio sepa nada sobre dónde vive esa caché. Por debajo puede haber un `Map` en memoria, Redis, Caffeine o cualquier otro backend — el código anotado es el mismo en los tres casos. Esta página cubre la parte que no depende del backend; [Redis como backend](./redis-como-backend) cubre la que sí.

## Activar la abstracción

```java
@Configuration
@EnableCaching
public class CacheConfig {
    // ...
}
```

`@EnableCaching` no es opcional ni implícito por tener `spring-boot-starter-cache` en el `pom.xml` — a diferencia de otras auto-configuraciones de Spring Boot, esta es opt-in deliberado. Sin esta anotación, `@Cacheable`/`@CacheEvict` se ignoran silenciosamente: no hay ningún error, simplemente nunca se cachea nada.

El `// ...` no es cosmético: esta misma clase `CacheConfig` también define, en la práctica, el `@Bean` que configura Redis como backend — TTL, serialización JSON — que se muestra completo en [Redis como backend](./redis-como-backend). Aquí se omite a propósito porque esta página cubre solo la parte que no depende del backend.

## `@Cacheable`

```java
@Component
public class CachedTaskLookup {

    private final TaskRepository taskRepository;

    public CachedTaskLookup(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Cacheable(value = "tasks", key = "#id")
    public Task findById(Long id) {
        return taskRepository.findByIdWithUser(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
```

`value = "tasks"` es el nombre de la caché (puede haber varias, cada una con su propia configuración); `key = "#id"` dice qué parte de los argumentos identifica la entrada — aquí, el id de la tarea. La primera llamada con un `id` dado ejecuta el método normalmente y guarda el resultado; las siguientes llamadas con el mismo `id` devuelven el valor guardado sin ejecutar el cuerpo del método — ni la consulta a `taskRepository` se dispara. Si el método lanza una excepción (por ejemplo `TaskNotFoundException`), no se cachea nada: solo se guardan retornos normales.

Y una decisión que parece un detalle y no lo es: el método llama a `findByIdWithUser(id)`, una consulta propia del repositorio (`select t from Task t join fetch t.user where t.id = :id`), y no al `findById` que `JpaRepository` ya da gratis. El motivo es que **lo que devuelve un método `@Cacheable` es exactamente lo que se guarda en la caché**, y `Task.user` es una relación `LAZY`: con `findById` a secas, ese `user` sería todavía un proxy de Hibernate sin cargar. En la caché en memoria de los tests eso pasa desapercibido, pero contra Redis el valor se serializa a JSON, y un proxy perezoso no sobrevive a ese viaje (su clase real es una subclase sintética generada en tiempo de ejecución, que no existe al volver a leer la entrada). El `join fetch` garantiza que lo que se cachea es un `User` real y ya cargado. `Task.user` sigue siendo `LAZY` por defecto para todo lo demás: la carga ansiosa está acotada al único camino que acaba en la caché.

## La trampa de la auto-invocación

`CachedTaskLookup` es una clase propia, separada de `TaskServiceImpl` — no un método privado de `TaskServiceImpl` que se llame a sí mismo. La razón no es de estilo: es necesaria para que `@Cacheable` funcione.

Spring implementa `@Cacheable` con un **proxy AOP**: envuelve el bean real en un objeto intermediario que intercepta las llamadas externas y decide si ejecuta el método real o devuelve el valor cacheado. Ese proxy solo entra en juego cuando la llamada llega **desde fuera** del bean, a través de la referencia que Spring inyectó. Una llamada `this.metodo()` hecha desde dentro de la propia clase nunca pasa por el proxy — la JVM la resuelve directamente sobre `this`, sin que Spring tenga oportunidad de interceptarla.

Si `findById` fuera un método público de `TaskServiceImpl` en vez de vivir en `CachedTaskLookup`, y otro método de esa misma clase lo llamara como `this.findById(id)`, la anotación `@Cacheable` se ignoraría por completo (y en un método `private` ni siquiera haría falta la auto-invocación: el proxy no puede interceptar métodos privados en ningún caso) — sin ningún error en consola, sin ninguna excepción, simplemente sin cachear nunca. Es uno de los bugs de Spring Cache más difíciles de detectar precisamente porque no falla de forma ruidosa.

```java
@Service
public class TaskServiceImpl implements TaskService {

    private final CachedTaskLookup cachedTaskLookup; // bean DISTINTO

    // ...

    private Task getTaskForCurrentUser(Long id, User currentUser) {
        Task task = cachedTaskLookup.findById(id); // llamada externa: SÍ pasa por el proxy
        requireAccess(task, currentUser);
        return task;
    }
}
```

`TaskServiceImpl` inyecta `CachedTaskLookup` como cualquier otra dependencia y lo llama desde fuera — esa llamada sí atraviesa el proxy de `CachedTaskLookup`, así que `@Cacheable` funciona de verdad.
