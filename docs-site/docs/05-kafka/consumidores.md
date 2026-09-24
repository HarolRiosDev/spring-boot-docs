---
title: Consumidores
sidebar_position: 2
---

# Consumidores

Del otro lado del topic `task-events` hay un componente que no sabe nada de HTTP, ni de `TaskServiceImpl`, ni de cómo se originó el mensaje — solo sabe leer un `TaskEvent` y guardar una notificación.

## `@KafkaListener`

```java
@Component
public class TaskEventListener {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public TaskEventListener(UserRepository userRepository, NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @KafkaListener(topics = "task-events")
    public void onTaskEvent(TaskEvent event) {
        userRepository.findByUsername(event.ownerUsername()).ifPresent(owner -> {
            String mensaje = switch (event.eventType()) {
                case CREATED -> "Se creó la tarea \"" + event.titulo() + "\"";
                case COMPLETED -> "Se completó la tarea \"" + event.titulo() + "\"";
            };
            notificationRepository.save(new Notification(event.taskId(), event.eventType(), mensaje, owner));
        });
    }
}
```

`@KafkaListener(topics = "task-events")` registra este método como el consumidor del topic — Spring Kafka lo llama automáticamente cada vez que llega un mensaje nuevo, deserializándolo directamente a `TaskEvent` (el mismo record que el productor serializó — ver [Productores y eventos](./productores-y-eventos)). No hay que escribir ningún bucle de polling a mano: Spring Kafka lo gestiona por debajo con un contenedor de listener, corriendo en su propio hilo, separado del hilo que atendió el request HTTP original.

## Qué pasa con un mensaje que no se puede procesar

`userRepository.findByUsername(event.ownerUsername())` puede no encontrar a nadie — no debería pasar en el flujo normal (el productor siempre usa un username real), pero un mensaje corrupto, o de una versión anterior del esquema, sí podría llegar así alguna vez. `ifPresent` lo ignora silenciosamente, sin lanzar una excepción: un usuario que ya no existe no es un error que merezca reintentar.

Pero hay fallos que sí son errores de verdad: la base de datos caída en el momento de guardar la notificación, o un mensaje que ni siquiera es JSON válido. Para esos casos, el ejemplo usa una **dead-letter queue** (DLQ, o *dead-letter topic* en Kafka): un topic aparte, `task-events-dlt`, donde acaban los mensajes que no se pudieron procesar, para revisarlos después en vez de perderlos.

## La dead-letter queue

```java
@Bean
public DefaultErrorHandler kafkaErrorHandler(
        KafkaTemplate<String, TaskEvent> kafkaTemplate,
        KafkaTemplate<String, byte[]> deadLetterBytesTemplate) {
    Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
    templates.put(byte[].class, deadLetterBytesTemplate);
    templates.put(TaskEvent.class, kafkaTemplate);
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
}
```

- `DefaultErrorHandler` decide qué hacer cuando el listener lanza una excepción. Con `FixedBackOff(1000L, 2)` reintenta el mismo mensaje 2 veces más, con 1 segundo entre intentos, por si el fallo era pasajero (la base de datos reiniciándose, por ejemplo). Sin configurar nada, lo reintentaría 10 veces seguidas y después lo descartaría, dejando solo un error en el log.
- `DeadLetterPublishingRecoverer` es lo que pasa cuando se agotan los reintentos: en vez de descartar el mensaje, lo publica en `task-events-dlt` con varios headers que explican el fallo (la clase de la excepción, su mensaje y la traza). El nombre sale del topic original más el sufijo `-dlt`, que es el valor por defecto en Spring Kafka 4. En la versión 3 era `.DLT`, así que muchos tutoriales usan `task-events.DLT`.
- En cualquiera de los dos casos, el consumidor pasa al mensaje siguiente: un mensaje defectuoso nunca bloquea a los que vienen detrás.

El mapa de templates existe por el caso más traicionero, el mensaje que no es JSON válido (un *poison pill*). Ese mensaje falla antes de llegar al listener, al deserializarse, así que no hay ningún `TaskEvent` que reenviar: solo los bytes originales, que necesitan un template que los publique sin transformarlos.

Para que ese fallo llegue al `DefaultErrorHandler`, el deserializador JSON va envuelto en un `ErrorHandlingDeserializer`:

```java
JacksonJsonDeserializer<TaskEvent> jsonDeserializer =
        new JacksonJsonDeserializer<>(TaskEvent.class).trustedPackages(TRUSTED_PACKAGE);
ErrorHandlingDeserializer<TaskEvent> deserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);
```

Sin ese envoltorio, la excepción de deserialización ocurre dentro del propio cliente de Kafka, antes de que Spring pueda intervenir: el consumidor vuelve a leer el mismo mensaje roto en cada intento y se queda atascado en él. `ErrorHandlingDeserializer` captura la excepción y entrega el mensaje con valor `null` y el error en un header, de modo que el `DefaultErrorHandler` lo trata como cualquier otro fallo. Como reintentar un JSON inválido no lo va a arreglar, esos errores no se reintentan y van directos a la DLT.

`DeadLetterTopicTest` lo comprueba de punta a punta contra el broker embebido: escribe `"esto no es un TaskEvent en JSON"` directamente en `task-events`, confirma que aparece intacto en `task-events-dlt` con la causa `DeserializationException` en su header, y después crea una tarea normal para comprobar que su notificación sigue llegando.

## Un consumidor no sabe quién lo llamó

Vale la pena notar lo que el `TaskEventListener` **no** tiene: no recibe ningún `User currentUser`, no hay contexto de seguridad de la petición HTTP original — para cuando este método se ejecuta, esa petición ya terminó y ya respondió. Todo lo que el consumidor necesita para actuar correctamente viene dentro del propio `TaskEvent` (`ownerUsername`, `eventType`, `titulo`) — por eso ese record tiene que llevar toda la información necesaria, no solo un id que obligue a ir a buscar el resto a otro sitio.
