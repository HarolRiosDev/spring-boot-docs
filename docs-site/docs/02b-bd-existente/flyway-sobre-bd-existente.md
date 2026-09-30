---
title: Flyway sobre una base de datos existente
sidebar_position: 2
---

# Flyway sobre una base de datos existente

En la [Fase 2](/docs/02-persistencia/flyway) Flyway creaba el esquema desde cero con `V1__create_tasks_table.sql`. Aquí el esquema ya existe y lo creó otro. Hay dos formas razonables de trabajar, y cuál toca depende de quién decide los cambios.

## Opción 1: sin migraciones

El DBA es el dueño del esquema: los cambios se piden, se revisan y los aplica él con su propio proceso. La aplicación no ejecuta DDL nunca; solo lo usa.

En ese caso Flyway sobra. Basta con no incluir la dependencia (o desactivarlo con `spring.flyway.enabled: false`) y dejar `ddl-auto: validate`: si alguien cambia el esquema de una forma que rompe tus entidades, la aplicación se niega a arrancar y el error dice qué columna o tabla no encaja.

## Opción 2: adoptar el esquema con un baseline

El equipo asume a partir de ahora los cambios que necesita su aplicación, pero sin tocar lo que ya había. Flyway lo resuelve con un *baseline*: una marca en su historial que dice "todo lo que existe hasta aquí es la versión 1; no lo ejecutes, empieza a contar desde ahí". Es lo que hace el ejemplo:

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 1
    baseline-description: esquema heredado
```

`baseline-version: 1` es el valor por defecto; se deja escrito para que se lea qué versión representa el esquema heredado. La primera vez que la aplicación arranca contra la base de datos heredada, Flyway ve un esquema con tablas pero sin su tabla de historial, así que hace el baseline y después aplica las migraciones posteriores:

```
Creating Schema History table "public"."flyway_schema_history" with baseline ...
Successfully baselined schema with version: 1
Current version of schema "public": 1
Migrating schema "public" to version "2 - control de concurrencia"
Successfully applied 1 migration to schema "public", now at version v2
```

El historial queda así:

```
 installed_rank | version |       description       |   type
----------------+---------+-------------------------+----------
              1 | 1       | esquema heredado        | BASELINE
              2 | 2       | control de concurrencia | SQL
```

Por eso la primera migración propia es `V2__control_de_concurrencia.sql` y no `V1`: la versión 1 es el esquema heredado. Una migración con versión menor o igual que la del baseline no se ejecuta nunca en esta base de datos. Flyway no da ningún error; simplemente la salta.

La `V2` del ejemplo añade una columna de versión a `TB_TAREA` y cambia el trigger para que la incremente. Para qué sirve se cuenta en [Vistas y convivencia con otras aplicaciones](./vistas-y-convivencia).

## Flyway va antes que Hibernate

Al arrancar, Spring Boot ejecuta Flyway antes de crear el `EntityManagerFactory`. Es decir, las migraciones se aplican antes de que `validate` compruebe las entidades. Por eso la entidad `Task` puede mapear `NU_VERSION` aunque el esquema heredado no la tenga: cuando Hibernate valida, la `V2` ya la ha creado.

## Con cuidado: `baseline-on-migrate`

`baseline-on-migrate` es cómodo, pero adopta lo que encuentre:

- **Una base de datos equivocada** (por ejemplo, otro esquema de otra aplicación con las credenciales de la tuya): Flyway la marca como versión 1 y le aplica tus migraciones.
- **Una base de datos vacía** (por ejemplo, el contenedor arrancó sin el script del DBA): no hay nada que adoptar. Flyway lo avisa y sigue con las migraciones, que no pueden funcionar:

```
All configured schemas are empty; a baseline marker will not be added to Flyway's schema history table.
Migration of schema "public" to version "2 - control de concurrencia" failed! Changes successfully rolled back.
ERROR: relation "tb_tarea" does not exist
```

La aplicación no arranca, que en este caso es lo correcto. Si prefieres que la adopción sea un paso consciente y no algo automático, deja `baseline-on-migrate` desactivado y ejecuta el baseline una sola vez a mano, con la línea de comandos o el plugin de Maven de Flyway, sobre la base de datos correcta.

## ¿Y los cambios que haga el DBA después?

Adoptar el esquema con Flyway no impide que el DBA siga cambiándolo por su lado, y Flyway no se entera de esos cambios. `validate` es lo que te protege: si un cambio ajeno rompe algo que tus entidades usan, lo verás al arrancar. Aun así, en una base de datos compartida el esquema es un acuerdo entre equipos, no algo que decida una migración: antes de escribir una `V3` que toca una tabla que usan otros, habla con ellos.
