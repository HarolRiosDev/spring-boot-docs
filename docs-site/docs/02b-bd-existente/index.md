---
title: Base de datos existente
---

# Base de datos existente

En la [Fase 2](/docs/02-persistencia/) la aplicación era dueña de su base de datos: Flyway creaba la tabla `tasks` desde cero y la entidad `Task` elegía los nombres. En el trabajo real es muy habitual lo contrario. Llegas a una base de datos que **ya existe**: la diseñó otro equipo hace años, la gestiona un DBA y otras aplicaciones escriben en ella. No puedes renombrar una columna porque no te guste, ni cambiar un tipo, ni borrar un trigger que otra aplicación necesita.

Esta sección enseña a trabajar así: la aplicación deja de decidir el esquema y se adapta a él.

:::info[Antes de empezar]
Esta sección da por hecha la [Fase 2](/docs/02-persistencia/): entidades, repositorios y Flyway. Sus tests usan Testcontainers, que se explica a fondo en la [Fase 6](/docs/06-testing/); aquí se cuenta solo lo necesario.
:::

## El esquema heredado del ejemplo

El ejemplo trae un script, `docker/legacy-schema.sql`, que hace de "lo que dejó el DBA". Lo ejecuta Postgres al crear el contenedor, nunca la aplicación:

| Objeto | Qué es | Qué lo complica |
|---|---|---|
| `TB_TAREA` | Las tareas | Nombres con prefijos (`DS_TITULO`, `FL_COMPLETADA`), una bandera `CHAR(1)` con `'S'`/`'N'` y fechas que rellena la propia base de datos |
| `SQ_TAREA` | La secuencia de ids | Avanza de uno en uno y otra aplicación también la usa |
| `TB_COMENTARIO_TAREA` | Los comentarios de cada tarea | Clave primaria compuesta `(ID_TAREA, NU_LINEA)` y clave foránea sin `ON DELETE CASCADE` |
| `TR_TAREA_MODIFICADA` | Un trigger | Escribe `FH_MODIFICACION` a espaldas de la aplicación |
| `VW_RESUMEN_TAREA` | Una vista | Se lee como una tabla, pero no se puede escribir en ella |
| `FN_PURGAR_COMPLETADAS` | Una función en PL/pgSQL | Lógica de negocio que vive en la base de datos |

Los prefijos son una convención muy extendida en esquemas de empresa: `TB_` tabla, `VW_` vista, `SQ_` secuencia, `ID_` identificador, `DS_` descripción o texto, `FL_` bandera (*flag*), `FH_` fecha y hora, `NU_` número.

## Contenido

1. [Mapear un esquema heredado](./mapear-esquema-heredado)
2. [Flyway sobre una base de datos existente](./flyway-sobre-bd-existente)
3. [Vistas y convivencia con otras aplicaciones](./vistas-y-convivencia)
4. [SQL fuera de JPA](./sql-fuera-de-jpa)
5. [Probar contra el esquema real](./probar-contra-el-esquema-real)

## Ejemplo ejecutable

Todo el código de esta sección vive en [`examples/02b-bd-existente`](https://github.com/HarolRiosDev/spring-boot-docs/tree/main/examples/02b-bd-existente): el mismo dominio de tareas, ahora sobre un esquema que no es suyo.

```bash
cd examples/02b-bd-existente
docker compose up -d        # Postgres con el esquema heredado en localhost:5439
./mvnw spring-boot:run
```

Aquí no hay perfil `h2`: sin la base de datos heredada no hay nada que mapear. Si no quieres usar `docker compose`, `./mvnw spring-boot:test-run` arranca la aplicación contra un Postgres de Testcontainers con el mismo script.
