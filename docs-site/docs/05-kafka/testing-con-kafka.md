---
title: Testing con Kafka
sidebar_position: 3
---

# Testing con Kafka

Todos los tests de este sitio, hasta ahora, tenían un patrón en común: hacer una petición, y comprobar la respuesta inmediatamente después. Con Kafka eso deja de funcionar — el consumidor procesa el mensaje en su propio hilo, en su propio momento, después de que la petición HTTP ya respondió. Antes de ver el código, por qué la forma obvia de probarlo está mal.

## Por qué una aserción inmediata falla (a veces)

```java
mockMvc.perform(post("/tasks")...).andExpect(status().isCreated());

// mal: el consumidor puede no haber procesado el mensaje todavía
List<Notification> notifications = notificationRepository.findByUser(user);
assertThat(notifications).hasSize(1); // falla al azar, dependiendo de qué tan rápido corra el consumidor
```

Este test puede pasar en una máquina rápida y fallar en CI, o pasar nueve de cada diez veces — un test **flaky**, que falla de forma intermitente sin que el código tenga ningún bug real. El problema no es el código de producción, es que la aserción asume sincronía donde no la hay.

## `Awaitility`: esperar una condición, no un tiempo fijo

```java
await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
    User user = userRepository.findByUsername(username).orElseThrow();
    List<Notification> notifications = notificationRepository.findByUser(user);
    assertThat(notifications).hasSize(1);
});
```

`await().untilAsserted(...)` reintenta el bloque que le pasas hasta que las aserciones de dentro dejan de lanzar excepción, o hasta agotar el tiempo máximo (`atMost`) — en ese caso, sí falla, con un mensaje claro. La alternativa obvia, un `Thread.sleep(2000)` fijo antes de comprobar, tiene el problema inverso: si el consumidor tarda más de 2 segundos (una máquina cargada, CI más lento de lo normal), el test falla igual — y si tarda menos, el test es más lento de lo necesario esperando sin motivo. `await()` no tiene ninguno de los dos problemas: reacciona en cuanto la condición es cierta, y solo falla si de verdad nunca llega a serlo.

## `@EmbeddedKafka`: un broker real, no un mock

```java
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "task-events")
class TaskEventFlowTest {
    // ...
}
```

`@EmbeddedKafka` levanta un broker Kafka de verdad dentro del propio proceso de test — no una simulación del cliente, un broker real hablando el protocolo real de Kafka, sin necesitar Docker. El productor (`TaskEventPublisher`) y el consumidor (`TaskEventListener`) de la aplicación real se conectan a él exactamente igual que se conectarían a un Kafka de producción — lo único embebido es el broker, no el código que se está probando.

## Probar que algo *no* pasó, sin `Thread.sleep`

Probar que un evento se publicó es fácil con `await()`: se espera hasta que aparezca. Pero, ¿cómo se prueba que un evento **no** se publicó — por ejemplo, que actualizar una tarea ya completada no genera un segundo evento `COMPLETED`? Esperar un tiempo fijo y comprobar que sigue sin aparecer tiene el mismo problema que un `Thread.sleep`: nunca hay garantía de haber esperado *lo suficiente*.

La técnica usada en este proyecto: disparar un evento real *adicional*, y esperar su notificación con `await()`. Como los mensajes de un topic con una sola partición se procesan en el orden en que llegaron, cuando la notificación "barrera" aparece, cualquier evento anterior que debiera haberse publicado ya se procesó — si no apareció para entonces, nunca lo hará:

```java
// (después de la acción que NO debería publicar un evento)

Long barrierTaskId = createTask(token, false); // una tarea real, de verdad
await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
    boolean barrierNotified = notificationRepository.findByUser(user).stream()
            .anyMatch(n -> n.getTaskId().equals(barrierTaskId));
    assertThat(barrierNotified).isTrue();
});

// ahora sí, la comprobación real: cero eventos COMPLETED para la tarea original
long completedCount = notificationRepository.findByUser(user).stream()
        .filter(n -> n.getTaskId().equals(originalTaskId) && n.getEventType() == TaskEventType.COMPLETED)
        .count();
assertThat(completedCount).isZero();
```

Es el mismo mecanismo que usa `TaskEventRulesTest` para confirmar, contra un broker Kafka real, que un rollback de transacción nunca llega a publicar nada: se fuerza el rollback, se dispara un evento barrera real de otro usuario, se espera esa notificación, y solo entonces se confirma que la lista de notificaciones del usuario original sigue vacía.
