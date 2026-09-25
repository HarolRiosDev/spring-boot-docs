---
title: Probar con Kafka real
sidebar_position: 4
---

# Probar con Kafka real

`EmbeddedKafkaBroker` (ver [Testing con Kafka](./testing-con-kafka)) confirma que el flujo funciona en un test automatizado, pero un broker embebido no es exactamente lo mismo que un Kafka real corriendo en Docker — vale la pena verlo funcionar contra el de verdad al menos una vez.

## Levantar Kafka real

```bash
cd examples/05-kafka
docker compose up -d
```

Levanta Kafka en modo KRaft — un único contenedor, sin Zookeeper (Kafka lo eliminó por completo en la versión 4) — escuchando en `localhost:9092`, más Postgres en `localhost:5437`.

## `GET /notifications`

Con la app corriendo (`./mvnw spring-boot:run`), crea una tarea con `POST /tasks` y luego consulta las notificaciones (el `$TOKEN` se obtiene como en [Probar endpoints protegidos](/docs/03-security-jwt/probar-endpoints-protegidos)):

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/notifications
```

Debería devolver una lista con la notificación de creación, con el mismo criterio de ownership que `/tasks`: cada usuario ve solo las suyas, salvo un `ADMIN`, que las ve todas.

## Ver los mensajes crudos en el topic

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic task-events --from-beginning
```

Cada línea es un mensaje JSON — el mismo `TaskEvent` que el productor serializa y el consumidor deserializa, visible tal cual viaja por el topic. Es la forma más directa de confirmar que lo que se está enseñando en las páginas anteriores no es una abstracción: hay un mensaje real, en un broker real, con esos campos exactos.

## Provocar un mensaje a la dead-letter queue

Para ver la [dead-letter queue](./consumidores#la-dead-letter-queue) en acción, escribe a mano en `task-events` algo que no sea un `TaskEvent` válido:

```bash
echo 'esto no es JSON' | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic task-events
```

La aplicación no se queda atascada en ese mensaje: lo manda a `task-events-dlt` y sigue con los siguientes. Para verlo allí, junto con los headers que explican por qué falló:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic task-events-dlt --from-beginning \
  --property print.headers=true
```

Entre los headers verás `kafka_dlt-exception-fqcn` con el valor `org.springframework.kafka.support.serializer.DeserializationException`: la causa exacta, guardada junto al mensaje original para poder investigarlo después.
