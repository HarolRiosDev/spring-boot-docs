---
title: Persistencia
---

# Persistencia

En la [Fase 1](/docs/01-fundamentos/) guardábamos las tareas en un mapa en memoria: útil para aprender capas, pero los datos desaparecían al reiniciar la aplicación. Esta fase sustituye ese repositorio por persistencia real: PostgreSQL como base de datos, Spring Data JPA para no escribir SQL a mano, y Flyway para versionar el esquema como código.

## Contenido

1. [Docker Compose y Postgres](./docker-compose-postgres)
2. [Migraciones con Flyway](./flyway)
3. [Spring Data JPA](./spring-data-jpa)
4. [Probar la app con datos reales](./probar-con-datos-reales)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/02-persistencia`](https://github.com/HarolRiosDev/spring-boot-docs/tree/main/examples/02-persistencia): la misma API de tareas de la Fase 1, ahora respaldada por Postgres.

```bash
cd examples/02-persistencia
docker compose up -d
./mvnw spring-boot:run
```
