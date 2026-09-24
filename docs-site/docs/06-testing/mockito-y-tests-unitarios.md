---
title: Mockito y tests unitarios
sidebar_position: 2
---

# Mockito y tests unitarios

Hasta esta fase, casi todos los tests del sitio han sido de integración: `@SpringBootTest` levanta el contexto completo de Spring, una base de datos real (H2), y se ejercita el API entero vía HTTP con MockMvc. Los pocos unitarios (`JwtServiceTest`, `GlobalExceptionHandlerTest`) probaban clases sin colaboradores, que bastaba con crear con `new`; lo nuevo aquí es aislar una clase que **sí** depende de otras. Es una herramienta potente, pero no la única — y saber cuándo cambiar a un test unitario puro con Mockito es, en la práctica, uno de los criterios que menos se enseña bien. Antes de ver código, el criterio.

## Cuándo sí, cuándo no

Un test **unitario** con Mockito tiene sentido cuando la clase bajo prueba tiene lógica de negocio no trivial y sus dependencias son fáciles de sustituir (interfaces, sin `final`) — el objetivo es probar esa lógica aislada, sin pagar el costo de levantar un contexto Spring ni una base de datos.

Un test de **integración** (lo ya conocido: `@SpringBootTest` + MockMvc) tiene sentido cuando lo que hay que confirmar es que las piezas están bien conectadas — que Spring Security bloquea lo que debe bloquear, que una consulta JPA devuelve lo que la anotación dice que devuelve, que la serialización JSON no rompe nada. Eso no se puede mockear: hay que ejercitarlo de verdad.

`TaskServiceImpl.findById` es un buen candidato para Mockito: la lógica de ownership (¿puede este usuario ver esta tarea?) vive enteramente en Java, sin tocar la base de datos directamente — solo llama a `CachedTaskLookup`, una clase de una línea fácil de sustituir (`@Component`, no una interfaz — Mockito mockea clases concretas no-`final` igual de bien que interfaces). Hasta ahora esa lógica solo se había probado indirectamente, vía HTTP con todo el contexto real.

## Errores comunes (y por qué importan)

- **Mockear todo, incluidos objetos simples.** Un `record` o una clase de valor sin lógica (como `TaskRequest`) no necesita mock — crear una instancia real es más simple y más fiel que simular su comportamiento.
- **Sobre-usar `verify()`.** Comprobar que un método se llamó exactamente una vez con exactamente estos argumentos es útil cuando ese llamado *es* el comportamiento a probar (por ejemplo, "se debe invalidar la caché al actualizar"). Usado en cada test, termina probando *cómo* está implementado el método en vez de *qué* devuelve — un refactor interno que no cambia el comportamiento rompe el test igualmente.
- **Confundir `@Mock`/`Mockito.mock()` con `@MockBean`/`@MockitoBean`.** `@Mock` (este test) crea un doble sin ningún Spring de por medio — rápido, aislado. `@MockBean`/`@MockitoBean` sustituye un bean *dentro* de un contexto Spring real (`@SpringBootTest` sigue arrancando) — resuelve un problema distinto: "quiero el contexto real, pero sin que este componente concreto llame a un servicio externo". Son herramientas para problemas distintos, no intercambiables.
- **No resetear stubs entre tests cuando hace falta.** `@ExtendWith(MockitoExtension.class)` crea mocks nuevos por cada método de test por defecto — no es necesario resetear a mano, pero si un mock se comparte a propósito entre tests (por ejemplo, vía un campo `static`), hay que ser explícito sobre por qué.

## El test

```java
@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CachedTaskLookup cachedTaskLookup;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(taskRepository, cachedTaskLookup);
    }

    @Test
    void findById_asDifferentUser_throwsAccessDenied() {
        Task task = taskOwnedBy(owner, 10L);
        when(cachedTaskLookup.findById(10L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.findById(10L, otherUser))
                .isInstanceOf(TaskAccessDeniedException.class);
    }

    // ...
}
```

`@Mock` crea el doble; `when(...).thenReturn(...)` programa su respuesta; `taskService` recibe esos dobles por constructor, como recibiría las implementaciones reales — **la clase bajo prueba no sabe que está siendo probada con mocks**. Ni `taskRepository` ni `cachedTaskLookup` tocan una base de datos: la aserción confirma únicamente la lógica de `requireAccess` dentro de `TaskServiceImpl`.

Un detalle que aparece la primera vez aquí: `User`/`Task` tienen un campo `id` que JPA rellena automáticamente al guardar en una base de datos real — en un test que nunca toca la base de datos, ese campo queda en `null` a menos que se fuerce con `ReflectionTestUtils.setField(objeto, "id", valor)` (de `spring-test`). No es una solución elegante, es un recordatorio honesto de que un test unitario puro a veces tiene que trabajar un poco más para simular lo que la infraestructura real da gratis.
