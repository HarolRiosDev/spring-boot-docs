---
title: Paginación, ordenación y filtros
---

# Paginación, ordenación y filtros

Hasta ahora, `GET /tasks` devolvía todas las tareas de golpe. Con diez tareas no se nota. Con cien mil, cada petición lee la tabla entera, la convierte en objetos Java, la serializa en un JSON de varios megas y la manda por la red, aunque la pantalla que la pidió solo vaya a enseñar veinte. Y basta con que un cliente pida el listado en bucle para que toda la aplicación lo note.

Esta sección enseña a servir los listados **por páginas**, en un orden que no cambia de una página a otra, con filtros opcionales y sin dejar que un cliente pida más de la cuenta. Todo con lo que ya trae Spring Data: no hace falta escribir a mano el SQL de la paginación.

:::info[Antes de empezar]
Esta sección parte de la [Fase 2](/docs/02-persistencia/): entidades, repositorios y Flyway. Los ejemplos de las fases siguientes (3 a 7) siguen devolviendo la lista completa, para no mezclar temas.
:::

## Qué cambia respecto a la Fase 2

| | Fase 2 | Esta sección |
|---|---|---|
| `GET /tasks` | Todas las tareas, en una lista | Una página: `?page=`, `?size=` y `?sort=`, más los filtros `?completada=` y `?q=` |
| Orden | El que devuelva la base de datos | Las más recientes primero, siempre en el mismo orden |
| `Task` | `titulo`, `descripcion`, `completada` | Además, `fechaCreacion` |
| Datos al arrancar | Ninguno | 60 tareas de ejemplo (migración `V3`) |
| Endpoint nuevo | — | `GET /tasks/recientes`, un listado "cargar más" que no cuenta el total |

## Contenido

1. [Paginar y ordenar](./paginar-y-ordenar)
2. [Límites y errores](./limites-y-errores)
3. [Filtros](./filtros)
4. [Slice vs Page](./slice-vs-page)
5. [Probar la paginación](./probar-la-paginacion)

## Ejemplo ejecutable

Todo el código de esta sección vive en [`examples/02c-paginacion`](https://github.com/HarolRiosDev/spring-boot-docs/tree/main/examples/02c-paginacion), una copia del ejemplo de la Fase 2 con lo que se explica aquí.

```bash
cd examples/02c-paginacion
docker compose up -d        # Postgres en localhost:5440
./mvnw spring-boot:run
```

Sin Docker, el perfil `h2` arranca la aplicación contra una base H2 guardada en un fichero local, con las mismas migraciones y las mismas 60 tareas:

```bash
SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run
```

Y ya puedes pedir la primera página:

```bash
curl "http://localhost:8080/tasks?size=3"
```
