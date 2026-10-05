---
title: Probar la paginación
sidebar_position: 5
---

# Probar la paginación

## Con `curl`

Con la aplicación arrancada (con `docker compose` o con el perfil `h2`), estas peticiones recorren todo lo que cuenta la sección. Las comillas son necesarias: sin ellas, la terminal corta la URL en el `&`.

```bash
curl "http://localhost:8080/tasks?size=3"                         # primera página de 3
curl "http://localhost:8080/tasks?size=3&page=1"                  # la siguiente
curl "http://localhost:8080/tasks?sort=titulo&size=5"             # por título
curl "http://localhost:8080/tasks?page=999"                       # vacía, con los totales
curl "http://localhost:8080/tasks?size=5000"                      # recortada a 100
curl "http://localhost:8080/tasks?sort=noexiste"                  # 400: campo no permitido
curl "http://localhost:8080/tasks?completada=quizas"              # 400: tipo incorrecto
curl "http://localhost:8080/tasks?completada=true"                # solo completadas
curl "http://localhost:8080/tasks?completada=false&q=informe"     # los dos filtros
curl "http://localhost:8080/tasks?q=%25"                          # un % literal (%25 en la URL)
curl "http://localhost:8080/tasks/recientes?size=3"               # Slice: hasNext, sin totales
curl "http://localhost:8080/tasks/recientes?size=20&page=2"       # la última: hasNext false
```

`%` es un carácter especial también en las URLs, así que para buscarlo se escribe `%25`.

## Ver el SQL

Para ver las consultas que lanza Hibernate, añade temporalmente a `src/main/resources/application.yml`:

```yaml
logging:
  level:
    org.hibernate.SQL: debug
```

Con eso puedes comprobar lo que cuentan las páginas anteriores: el `order by … id desc` del desempate, el `like ? escape '\'` de los filtros, el `COUNT` de los `Page` (y las páginas en las que Spring Data se lo ahorra) y la consulta única del `Slice`.

## Los tests

```bash
./mvnw test
```

Corren contra H2 en memoria, sin Docker. Son dos clases de MockMvc: `TaskControllerTest`, el CRUD de la Fase 2 adaptado, y `TaskPaginationTest`, todo lo de esta sección.

### Comprobar propiedades, no totales

Las dos clases comparten la misma base de datos en memoria, y `TaskControllerTest` crea tareas que se quedan en ella. Los tests no llevan `@Transactional`, con el mismo criterio que en [Probar contra el esquema real](/docs/02b-bd-existente/probar-contra-el-esquema-real). Así que un test de paginación no puede contar con que haya exactamente 60 tareas: depende de qué otros tests se hayan ejecutado antes.

En vez de totales, los tests comprueban **propiedades** que se cumplen haya las tareas que haya: "ninguna de las devueltas está pendiente", "todas contienen *informe*", "van de la más reciente a la más antigua". Por ejemplo:

```java
@Test
void listTasks_filteredByCompletada_returnsOnlyCompletedTasks() throws Exception {
    mockMvc.perform(get("/tasks").param("completada", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isNotEmpty())
            .andExpect(jsonPath("$.content[?(@.completada == false)]").isEmpty());
}
```

### El test del desempate

El fallo del desempate no se puede provocar a voluntad: depende del orden en que la base de datos devuelva las filas empatadas. Así que el test no busca el fallo, comprueba la solución: recorre **todas** las páginas y exige que cada tarea salga una sola vez y que, entre las que tienen la misma fecha, vaya primero la de mayor `id`.

```java
@Test
void walkingAllPages_returnsEveryTaskOnceInAStableOrder() throws Exception {
    JsonNode first = getJson(get("/tasks").param("size", "7"));
    int totalPages = first.get("page").get("totalPages").asInt();
    long totalElements = first.get("page").get("totalElements").asLong();

    List<JsonNode> tasks = new ArrayList<>();
    for (int page = 0; page < totalPages; page++) {
        JsonNode body = getJson(get("/tasks").param("size", "7").param("page", String.valueOf(page)));
        tasks.addAll(toList(body.get("content")));
    }

    List<Long> ids = tasks.stream().map(task -> task.get("id").asLong()).toList();
    assertThat(ids).hasSize((int) totalElements).doesNotHaveDuplicates();
    assertNewestFirst(tasks);
}
```

```java
// Más reciente primero y, entre tareas con la misma fecha, el id mayor primero
private static void assertNewestFirst(List<JsonNode> tasks) {
    for (int i = 1; i < tasks.size(); i++) {
        JsonNode previous = tasks.get(i - 1);
        JsonNode current = tasks.get(i);
        Instant previousDate = Instant.parse(previous.get("fechaCreacion").asString());
        Instant currentDate = Instant.parse(current.get("fechaCreacion").asString());
        assertThat(currentDate).isBeforeOrEqualTo(previousDate);
        if (currentDate.equals(previousDate)) {
            assertThat(current.get("id").asLong()).isLessThan(previous.get("id").asLong());
        }
    }
}
```

En las pruebas, al quitar el desempate del servicio, este test falló: entre las tareas 21 a 24, H2 no devolvía el `id` mayor primero.

### Por qué aquí basta H2

En la [sección 2b](/docs/02b-bd-existente/probar-contra-el-esquema-real) los tests necesitaban un PostgreSQL real, porque el esquema dependía de PL/pgSQL. Aquí todo el SQL es estándar (`LIKE`, `lower`, `ORDER BY`, `OFFSET … FETCH`), y las diferencias entre H2 y PostgreSQL que afectan a la paginación están acotadas: los `NULL` de `descripcion` quedan fuera de la lista de campos ordenables, ningún test depende del orden alfabético y las tareas de partida no dependen de cómo trate `lower()` las mayúsculas con tilde. Aun así, merece la pena arrancar la aplicación con `docker compose` y repetir las peticiones de arriba contra PostgreSQL: los tests prueban la lógica, no el motor de base de datos que usarás en producción.
