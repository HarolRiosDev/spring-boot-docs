---
title: Probar con Kafka real
sidebar_position: 4
---

# Probar con Kafka real

`EmbeddedKafkaBroker` (ver [Testing con Kafka](./testing-con-kafka)) confirma que el flujo funciona en un test automatizado, pero un broker embebido no es exactamente lo mismo que un Kafka real corriendo en Docker — vale la pena verlo funcionar contra el de verdad al menos una vez.

## Levantar Kafka real

```bash
docker compose up -d
```

Levanta Kafka en modo KRaft — un único contenedor, sin Zookeeper (Kafka lo eliminó por completo en la versión 4) — escuchando en `localhost:9092`, más Postgres en `localhost:5437`.

## `GET /notifications`

Con la app corriendo (`./mvnw spring-boot:run`), crear una tarea normalmente vía `POST /tasks` y luego consultar:

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
