---
title: Filtros
sidebar_position: 3
---

# Filtros

Un listado paginado casi nunca va solo: el cliente quiere "las pendientes", "las que tienen *informe* en el título" o las dos cosas a la vez. El ejemplo admite dos filtros opcionales en `GET /tasks`:

- `?completada=true` o `?completada=false`.
- `?q=texto`: tareas cuyo título contiene ese texto, sin distinguir mayúsculas.

Se combinan entre sí y con la paginación: `?completada=false&q=informe&size=5&page=0`.

## Un filtro: un método derivado

Con un solo filtro, la [Fase 2](/docs/02-persistencia/spring-data-jpa) ya da la solución: un método derivado. Si recibe un `Pageable` y devuelve un `Page`, Spring Data añade el `LIMIT/OFFSET` y la consulta del total por su cuenta:

```java
// Primer intento (no está así en el ejemplo)
Page<Task> findByCompletada(boolean completada, Pageable pageable);
```

Y en el servicio, un `if`: si `completada` es `null`, `findAll(pageable)`; si no, `findByCompletada(completada, pageable)`.

## Dos filtros opcionales: los métodos se multiplican

Ahora añade `q`. Cada filtro puede venir o no, así que hacen falta cuatro métodos y un `if` que elija entre ellos:

```java
// Primer intento (no está así en el ejemplo)
Page<Task> findAll(Pageable pageable);
Page<Task> findByCompletada(boolean completada, Pageable pageable);
Page<Task> findByTituloContainingIgnoreCase(String q, Pageable pageable);
Page<Task> findByCompletadaAndTituloContainingIgnoreCase(boolean completada, String q, Pageable pageable);
```

Con un tercer filtro serían ocho, y con un cuarto, dieciséis. No escala.

## `Specification`: condiciones que se combinan

Una `Specification<Task>` es una condición del `WHERE` metida en un objeto: se pueden crear por separado y combinarlas después con `and`. Para usarlas, el repositorio extiende también `JpaSpecificationExecutor`, que añade, entre otros, un `findAll(Specification<Task> spec, Pageable pageable)`:

```java
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
    // ...
}
```

Las condiciones viven en una clase aparte, una por filtro:

```java
// Cada método devuelve una condición del WHERE; el servicio combina solo las que necesita
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> conCompletada(boolean completada) {
        return (root, query, cb) -> cb.equal(root.get("completada"), completada);
    }

    // Sin distinguir mayúsculas: se compara lower(titulo) con el texto en minúsculas
    public static Specification<Task> tituloContiene(String texto) {
        String patron = "%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("titulo")), patron, '\\');
    }

    // En LIKE, % y _ son comodines: sin escaparlos, buscar "%" encontraría todas las tareas
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
```

Cada `Specification` es una lambda que recibe tres cosas: `root`, la entidad sobre la que se consulta (el `FROM tasks`); `query`, la consulta entera, que aquí no hace falta; y `cb`, un `CriteriaBuilder`, la fábrica de expresiones de JPA (`equal`, `like`, `lower`…).

El servicio empieza sin condiciones y añade solo las que vengan en la petición:

```java
// Se empieza sin condiciones y se añade solo lo que venga en la petición
Specification<Task> spec = Specification.unrestricted();
if (completada != null) {
    spec = spec.and(TaskSpecifications.conCompletada(completada));
}
if (StringUtils.hasText(q)) {
    spec = spec.and(TaskSpecifications.tituloContiene(q.trim()));
}
return taskRepository.findAll(spec, conDesempate(pageable));
```

Con un tercer filtro, una condición más y un `if` más: nada se multiplica. Spring Data convierte la combinación en un único `WHERE`. Este es el SQL de `?completada=false&q=informe`:

```sql
select t1_0.id,t1_0.completada,t1_0.descripcion,t1_0.fecha_creacion,t1_0.titulo from tasks t1_0 where t1_0.completada=? and lower(t1_0.titulo) like ? escape '\' order by t1_0.fecha_creacion desc,t1_0.id desc offset ? rows fetch first ? rows only
```

Y la respuesta:

```json
{
  "content": [
    {
      "titulo": "Preparar el informe anual para la junta",
      "descripcion": "Con las cifras del año",
      "completada": false,
      "fechaCreacion": "2026-09-26T09:00:00Z",
      "id": 53
    },
    {
      "titulo": "Resumir el informe de la auditoría",
      "descripcion": "Solo las conclusiones",
      "completada": false,
      "fechaCreacion": "2026-09-18T17:00:00Z",
      "id": 38
    },
    {
      "titulo": "Preparar el informe trimestral de ventas",
      "descripcion": "Datos hasta septiembre",
      "completada": false,
      "fechaCreacion": "2026-09-12T09:00:00Z",
      "id": 25
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 3,
    "totalPages": 1
  }
}
```

`totalElements` cuenta solo las tareas que cumplen los filtros, no las 60.

Dos detalles de los filtros. `StringUtils.hasText` trata igual un `q` que no viene que uno vacío o con solo espacios (`?q=` o `?q=%20%20`): ninguno filtra nada. Y un `?completada=` vacío llega como `null`, así que tampoco filtra. Es lo que manda un formulario cuyo campo se dejó sin rellenar.

:::caution[Spring Data 4: `and(null)` ya no vale]
Muchos tutoriales, escritos para Spring Data 3, empiezan con `Specification.where(null)` y escriben condiciones que devuelven `null` cuando el filtro no viene, para que `and` las ignore. En Spring Data JPA 4 eso falla: `spec.and(null)` lanza `IllegalArgumentException: Other specification must not be null`. Empieza con `Specification.unrestricted()` y añade las condiciones con un `if`, como arriba.
:::

Los nombres de los atributos van como texto (`"completada"`, `"titulo"`): si te equivocas al escribirlos, no lo sabrás hasta ejecutar la consulta. El *metamodelo estático* de JPA (unas clases como `Task_`, generadas al compilar con `hibernate-processor`) permite escribir `root.get(Task_.titulo)` y que el compilador lo compruebe. Y Spring Data JPA 4 trae además `PredicateSpecification<T>`, una variante más sencilla de la misma idea (`(from, cb) -> …`, sin el parámetro `query`). Aquí se usa la `Specification` clásica porque es la que vas a encontrar en casi todo el código existente.

## `LIKE` y sus comodines

En `LIKE`, dos caracteres tienen un significado especial: `%` es "cualquier texto" y `_`, "cualquier carácter". Si el texto que escribe el usuario se pega tal cual al patrón, buscar `%` se convierte en `%%%`, que encaja con todo: buscar un `%` devolvería las 60 tareas. Lo mismo con `_`.

Por eso `escaparLike` pone una barra delante de `%`, `_` y de la propia barra (esta, la primera, para no duplicar las que añaden las otras dos), y `cb.like(..., '\\')` le dice a la base de datos que la barra es el carácter de escape: el `escape '\'` del SQL de arriba. Con eso, buscar un `%` encuentra solo la tarea que de verdad lo contiene. En la URL, ese `%` se escribe `%25`, porque `%` también es un carácter especial en las URLs (`%20`, por ejemplo, es un espacio):

```bash
curl "http://localhost:8080/tasks?q=%25"
```

```json
{
  "content": [
    {
      "titulo": "Subir la cobertura de tests al 100%",
      "descripcion": "Empezar por el servicio",
      "completada": false,
      "fechaCreacion": "2026-09-07T09:00:00Z",
      "id": 13
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

Si escribes el `%` tal cual (`?q=%` o `?q=100%`), Tomcat no puede decodificar el parámetro: la petición no llega al controlador y la respuesta es un 400 con el formato de error por defecto de Spring Boot, no con `ApiError`.

:::note[Mayúsculas con tilde y PostgreSQL]
`lower()` lo ejecuta la base de datos, y en PostgreSQL depende de su configuración regional. Con la de la imagen oficial que usa `docker compose` (`en_US.utf8`), `lower('É')` es `é`, así que `?q=épica` encontraría "ÉPICA: …". En una base creada con la configuración regional `C`, `lower` no toca las letras con tilde y esa búsqueda no encontraría nada. Las tareas del ejemplo no dependen de esto. Buscar sin tener en cuenta las tildes (que `?q=epica` encuentre "épica") es otro problema, que en PostgreSQL se resuelve con la extensión `unaccent` y queda fuera de esta sección.
:::
