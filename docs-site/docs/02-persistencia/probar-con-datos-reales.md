---
title: Probar la app con datos reales
sidebar_position: 4
---

# Probar la app con datos reales

Con Postgres, Flyway, la entidad y el repositorio ya en su sitio, así es como se conecta todo.

## Levantar la base de datos y la aplicación

```bash
docker compose up -d
cd examples/02-persistencia
./mvnw spring-boot:run
```

Al arrancar, Flyway aplica `V1__create_tasks_table.sql` contra el Postgres del contenedor (si no se había aplicado ya), y la aplicación queda escuchando en `http://localhost:8080`.

## Probar los endpoints

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"titulo": "Comprar leche", "descripcion": "2 litros", "completada": false}'

curl http://localhost:8080/tasks
```

Si detienes la aplicación y vuelves a arrancarla (sin borrar el volumen de Docker), las tareas siguen ahí — a diferencia de la Fase 1, donde se perdían al reiniciar.

## Cómo funcionan los tests sin Docker

`./mvnw test` **no necesita Docker ni Postgres**. Spring Boot usa automáticamente `src/test/resources/application.yml` en vez del de `src/main/resources` durante los tests, y ese archivo apunta a H2 (una base de datos en memoria) en modo de compatibilidad con PostgreSQL:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:tasks;MODE=PostgreSQL
    username: sa
    password:
  flyway:
    enabled: true
```

Flyway sigue activo en los tests: la misma migración `V1__create_tasks_table.sql` que crea la tabla en Postgres también la crea en H2. Así, `./mvnw test` comprueba de verdad que la migración es válida, no solo que el mapeo de la entidad es correcto — y ni tu máquina ni el CI necesitan Docker instalado para que la suite pase.

## `ddl-auto: validate`

Tanto `application.yml` como el de test fijan `spring.jpa.hibernate.ddl-auto: validate`. Esto le dice a Hibernate: "no crees ni modifiques el esquema tú — eso es trabajo de Flyway. Solo comprueba que la entidad `Task` coincide con la tabla real, y si no coincide, falla con un mensaje claro en vez de intentar arreglarlo por su cuenta." Es la combinación recomendada siempre que uses Flyway (o Liquibase) junto con JPA.
