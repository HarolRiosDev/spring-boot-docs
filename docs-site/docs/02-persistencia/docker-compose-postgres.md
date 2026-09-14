---
title: Docker Compose y Postgres
sidebar_position: 1
---

# Docker Compose y Postgres

Para desarrollar contra una base de datos real sin instalar PostgreSQL en tu máquina, usamos Docker Compose: describe qué contenedores necesitas en un archivo YAML, y un solo comando los levanta.

## El archivo `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: tasks
      POSTGRES_USER: tasks
      POSTGRES_PASSWORD: tasks
    ports:
      - "5433:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

- `image: postgres:16` — usa la imagen oficial de PostgreSQL 16 desde Docker Hub.
- `environment` — variables que la imagen oficial usa para crear la base de datos, el usuario y la contraseña la primera vez que arranca.
- `ports: "5433:5432"` — mapea el puerto 5433 de tu máquina al 5432 del contenedor (el puerto por defecto de Postgres). Usamos 5433 para no chocar si ya tienes Postgres instalado localmente en el 5432.
- `volumes` — un volumen con nombre para que los datos sobrevivan si detienes y vuelves a levantar el contenedor.

## Levantar y detener

```bash
docker compose up -d
```

El flag `-d` lo ejecuta en segundo plano. Para detenerlo:

```bash
docker compose down
```

(Añade `-v` a `down` si además quieres borrar el volumen y empezar de cero.)

## Cómo se conecta la aplicación

`application.yml` apunta directamente a este contenedor:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/tasks
    username: tasks
    password: tasks
```

Mientras el contenedor esté levantado, `./mvnw spring-boot:run` se conecta contra él automáticamente — no hace falta ninguna configuración adicional.
