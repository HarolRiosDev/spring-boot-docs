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

## Formas de inyectar dependencias

Cuando un bean necesita colaborar con otro (por ejemplo, `TaskServiceImpl` necesita un `TaskRepository`), Spring puede inyectar esa dependencia de varias formas. Solo una se recomienda, pero conviene reconocer las demás porque las vas a encontrar en código de otros equipos.

### Por constructor (la recomendada)

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

Spring ve que `TaskServiceImpl` necesita un `TaskRepository`, busca un bean que implemente esa interfaz (`InMemoryTaskRepository`, en nuestro ejemplo) y se lo pasa automáticamente al construir el objeto.

**¿Cuándo hace falta escribir `@Autowired` explícitamente sobre el constructor?** Si la clase tiene un único constructor —como aquí— no hace falta ninguna anotación: Spring lo detecta y lo usa sin que se lo digas. Solo necesitas anotar el constructor con `@Autowired` cuando la clase tiene **más de un constructor**, para indicarle a Spring cuál de ellos debe usar para inyectar dependencias; si hay varios y ninguno está anotado, Spring no sabe cuál elegir y el arranque falla.

### Por campo (`@Autowired` sobre el campo)

```java
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskRepository taskRepository;

    // ...
}
```

Spring asigna el valor directamente sobre el campo por reflection, después de construir el objeto con un constructor vacío. Es la forma más corta de escribir, y por eso aparece mucho en tutoriales — pero trae problemas reales: el campo no puede ser `final`, el objeto puede existir temporalmente sin su dependencia (no queda claro con solo mirar la clase qué necesita para funcionar), y para testear la clase sin arrancar Spring necesitas herramientas adicionales (`ReflectionTestUtils`, `@InjectMocks` de Mockito) en vez de un simple `new`.

### Por setter (`@Autowired` sobre el setter)

```java
@Service
public class TaskServiceImpl implements TaskService {

    private TaskRepository taskRepository;

    @Autowired
    public void setTaskRepository(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

Poco frecuente en la práctica. Tiene sentido solo para dependencias verdaderamente opcionales que quieras poder reconfigurar después de crear el bean. Igual que con la inyección por campo, el campo no puede ser `final`.

### Con Lombok: `@RequiredArgsConstructor`

En proyectos reales que ya usan [Lombok](https://projectlombok.org/) es muy común evitar escribir el constructor a mano:

```java
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    // sin constructor explícito: Lombok lo genera en tiempo de compilación
}
```

`@RequiredArgsConstructor` le dice a Lombok que genere, en tiempo de compilación, un constructor con un parámetro por cada campo `final` (o marcado `@NonNull`) de la clase. El resultado es exactamente el mismo bytecode que si hubieras escrito tú el constructor de la sección anterior — es puro ahorro de tecleo, no una forma distinta de inyección.

**El detalle que más confunde:** Lombok solo incluye en el constructor generado los campos `final` (o `@NonNull`). Si te olvidas de poner `final` en un campo, Lombok no avisa de nada — simplemente lo excluye del constructor, y ese campo queda `null` en tiempo de ejecución porque nadie lo inicializa nunca. Con `@RequiredArgsConstructor`, la regla es siempre: **todo campo que quieras que Spring inyecte tiene que ser `final`.**

Este sitio no usa Lombok en sus ejemplos ejecutables (para mantenerlos autocontenidos, sin dependencias de build adicionales), así que en `examples/01-fundamentos` verás el constructor de `TaskServiceImpl` escrito a mano — pero es idéntico en efecto a usar `@RequiredArgsConstructor`.

### Por qué constructor y no campo o setter

Con constructor (a mano o generado por Lombok) el objeto queda siempre en un estado válido: no puede existir sin su dependencia, porque Java no te deja construirlo sin pasarla. Eso permite que el campo sea `final` — nadie puede reasignarlo después por error — y hace que la clase sea trivial de testear: en un test puedes escribir `new TaskServiceImpl(unaImplementacionDePrueba)` sin necesitar arrancar Spring en absoluto. Con inyección por campo o por setter, en cambio, el objeto puede quedar a medio construir (con dependencias en `null`) y necesitas más herramientas para testearlo de forma aislada.

## Programar contra interfaces

`TaskServiceImpl` depende de la interfaz `TaskRepository`, no de la clase concreta `InMemoryTaskRepository`. Esto es lo que hace que la inyección de dependencias tenga sentido: en el futuro (Fase 2) podremos sustituir `InMemoryTaskRepository` por una implementación respaldada por una base de datos real, sin tocar ni una línea de `TaskServiceImpl`.

Puedes ver el patrón completo en [`examples/01-fundamentos`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/01-fundamentos): `TaskController` depende de `TaskService`, y `TaskServiceImpl` depende de `TaskRepository` — cada capa solo conoce la interfaz de la capa siguiente.
