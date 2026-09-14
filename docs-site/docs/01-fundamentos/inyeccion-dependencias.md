---
title: Inyección de dependencias y beans
sidebar_position: 1
---

# Inyección de dependencias y beans

Un **bean** es un objeto que Spring crea y gestiona por ti, en vez de que tú lo instancies con `new`. Spring guarda los beans en un contenedor (el *application context*) y los conecta entre sí automáticamente según lo que cada uno necesita — eso es la **inyección de dependencias (DI)**.

## Declarar un bean

La forma más común es anotar una clase con un estereotipo:

- `@Component` — un bean genérico.
- `@Service` — un bean de lógica de negocio.
- `@Repository` — un bean de acceso a datos.
- `@RestController` — un bean que expone endpoints HTTP.

```java
@Service
public class TaskServiceImpl implements TaskService {
    // ...
}
```

Spring detecta estas clases automáticamente al arrancar (*component scanning*) y crea una instancia de cada una.

## Inyección por constructor

Cuando un bean necesita colaborar con otro, la forma recomendada es recibirlo como parámetro del constructor:

```java
@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // ...
}
```

Spring ve que `TaskServiceImpl` necesita un `TaskRepository`, busca un bean que implemente esa interfaz (`InMemoryTaskRepository`, en nuestro ejemplo) y se lo pasa automáticamente. No hace falta ninguna anotación adicional en el constructor cuando la clase tiene un único constructor.

**¿Por qué por constructor y no con `@Autowired` en un campo?** Porque así el objeto queda siempre en un estado válido (no puede existir sin su dependencia), el campo puede ser `final`, y es trivial de testear: en un test puedes construir `new TaskServiceImpl(unaImplementacionDePrueba)` sin necesitar Spring en absoluto.

## Programar contra interfaces

`TaskServiceImpl` depende de la interfaz `TaskRepository`, no de la clase concreta `InMemoryTaskRepository`. Esto es lo que hace que la inyección de dependencias tenga sentido: en el futuro (Fase 2) podremos sustituir `InMemoryTaskRepository` por una implementación respaldada por una base de datos real, sin tocar ni una línea de `TaskServiceImpl`.

Puedes ver el patrón completo en [`examples/01-fundamentos`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/01-fundamentos): `TaskController` depende de `TaskService`, y `TaskServiceImpl` depende de `TaskRepository` — cada capa solo conoce la interfaz de la capa siguiente.
