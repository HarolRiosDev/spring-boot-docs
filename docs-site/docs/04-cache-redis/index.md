---
title: Caché y Redis
---

# Caché y Redis

Hasta ahora, cada `GET /tasks/{id}` de las Fases 1-3 volvía a consultar la base de datos, aunque fuera la misma tarea pedida diez veces seguidas. Esta fase añade una caché delante de esa lectura: la primera vez que alguien pide una tarea, la respuesta se guarda; las siguientes veces, mientras nada la haya invalidado, se sirve sin tocar Postgres. El API público no cambia ni una ruta — cachear es una capa transparente por debajo, no una funcionalidad nueva de cara al cliente.

## Contenido

1. [Spring Cache básico](./spring-cache-basico)
2. [Redis como backend](./redis-como-backend)
3. [Invalidación de caché](./invalidacion-de-cache)
4. [Probar con caché real](./probar-con-cache-real)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/04-cache-redis`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/04-cache-redis): la misma API de tareas de la Fase 3 (autenticación JWT, roles, ownership), ahora con caché sobre las lecturas.

```bash
cd examples/04-cache-redis
docker compose up -d
./mvnw spring-boot:run
```
