---
title: Mensajería con Kafka
---

# Mensajería con Kafka

Hasta ahora, cada acción del API terminaba en la respuesta HTTP. Esta fase añade algo que pasa *después*: crear o completar una tarea publica un evento a Kafka, y un consumidor separado — desacoplado del request original — lo procesa y guarda una notificación, sin que quien hizo la petición tenga que esperar a que eso termine.

## Contenido

1. [Productores y eventos](./productores-y-eventos)
2. [Consumidores](./consumidores)
3. [Testing con Kafka](./testing-con-kafka)
4. [Probar con Kafka real](./probar-con-kafka-real)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/05-kafka`](https://github.com/HarolRiosDev/spring-boot-docs/tree/main/examples/05-kafka): el mismo API de gestión de tareas con auth JWT y caché de la Fase 4, con notificaciones asíncronas añadidas encima.

```bash
cd examples/05-kafka
docker compose up -d
./mvnw spring-boot:run
```
