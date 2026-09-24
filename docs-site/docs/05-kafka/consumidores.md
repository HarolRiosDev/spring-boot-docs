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

`userRepository.findByUsername(event.ownerUsername())` puede no encontrar a nadie — no debería pasar en el flujo normal (el productor siempre usa un username real), pero un mensaje corrupto, o de una versión anterior del esquema, sí podría llegar así alguna vez. `ifPresent` lo ignora silenciosamente, sin lanzar una excepción. Si el listener lanzara una, el manejador de errores por defecto de Spring Kafka (`DefaultErrorHandler`) reintentaría el mismo mensaje hasta 10 veces seguidas y después lo descartaría, dejando solo un error en el log: diez intentos inútiles para un mensaje que nunca va a poder procesarse.

Esto es una simplificación deliberada. En un sistema real, un mensaje que no se puede procesar normalmente se manda a una **dead-letter queue** — un topic aparte donde se acumulan los mensajes fallidos para revisarlos manualmente, en vez de perderlos en silencio. Este ejemplo no la implementa, pero vale la pena saber que existe: es lo primero que se añadiría antes de llevar este patrón a producción de verdad (en Spring Kafka, con un `DeadLetterPublishingRecoverer` conectado al `DefaultErrorHandler`).

## Un consumidor no sabe quién lo llamó

Vale la pena notar lo que el `TaskEventListener` **no** tiene: no recibe ningún `User currentUser`, no hay contexto de seguridad de la petición HTTP original — para cuando este método se ejecuta, esa petición ya terminó y ya respondió. Todo lo que el consumidor necesita para actuar correctamente viene dentro del propio `TaskEvent` (`ownerUsername`, `eventType`, `titulo`) — por eso ese record tiene que llevar toda la información necesaria, no solo un id que obligue a ir a buscar el resto a otro sitio.
