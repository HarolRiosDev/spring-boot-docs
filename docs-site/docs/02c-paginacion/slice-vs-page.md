---
title: Slice vs Page
sidebar_position: 4
---

# Slice vs Page

## Lo que cuesta un `Page`

Un `Page` sabe cuántas tareas hay en total y cuántas páginas salen. Para saberlo, Spring Data lanza **dos** consultas por cada página: la de las filas y un `COUNT`. Este es el log de `?size=3`:

```sql
select t1_0.id,t1_0.completada,t1_0.descripcion,t1_0.fecha_creacion,t1_0.titulo from tasks t1_0 order by t1_0.fecha_creacion desc,t1_0.id desc offset ? rows fetch first ? rows only
select count(t1_0.id) from tasks t1_0
```

Con 60 filas, el `COUNT` es gratis. Con millones, y con filtros, puede costar más que la propia página: para contar, la base de datos tiene que recorrer todas las filas que cumplen el `WHERE`, no solo las 20 que vas a enseñar. Y lo repite en cada página.

Spring Data se ahorra el `COUNT` cuando puede deducir el total de las filas que ya tiene, es decir, cuando una página trae algo pero no se llena:

- Si es la primera, el total es lo que trae. Por eso `?size=5000` (60 tareas en una página de 100) o `?completada=false&q=informe` (3 resultados) lanzan una sola consulta.
- Si es otra, el total es lo que se ha saltado más lo que trae. `?size=7&page=8` se salta 56 tareas y trae las 4 últimas: 60, sin contar.

En el resto de casos hay `COUNT`: en cualquier página llena (aunque sea la última, como `?size=3&page=19`) y en una página vacía más allá del final, porque sin filas no hay de dónde deducir el total.

## `Slice`: solo "¿hay más?"

Muchas pantallas no necesitan el total. Un botón "cargar más" o un *scroll* infinito solo necesitan saber si hay algo después. Para eso está `Slice`: una página que no sabe cuántas hay en total, solo si existe una siguiente.

Para obtenerlo basta con que un método del repositorio devuelva `Slice`:

```java
// No puede llamarse findAll: JpaRepository ya tiene Page<Task> findAll(Pageable)
// y Java no permite dos métodos que solo se diferencian en el tipo de retorno
Slice<Task> findAllBy(Pageable pageable);
```

`findAllBy` es un método derivado sin condiciones: "todas", con el `Pageable` que se le pase. El truco de Spring Data está en el SQL. Con `?size=3`:

```sql
select t1_0.id,t1_0.completada,t1_0.descripcion,t1_0.fecha_creacion,t1_0.titulo from tasks t1_0 order by t1_0.fecha_creacion desc,t1_0.id desc fetch first ? rows only
```

Una sola consulta, sin `COUNT`. El parámetro de `fetch first` vale 4, uno más que el tamaño de página: si llega esa cuarta fila, hay siguiente; Spring Data la descarta y devuelve 3.

El ejemplo lo usa en `GET /tasks/recientes`, con un orden fijo que no depende del cliente:

```java
private static final Sort ORDEN_RECIENTES = Sort.by(Sort.Direction.DESC, "fechaCreacion", "id");

@Override
public Slice<Task> findRecientes(Pageable pageable) {
    // El orden es parte del significado de "recientes": se ignora el sort del cliente
    Pageable recientes = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDEN_RECIENTES);
    return taskRepository.findAllBy(recientes);
}
```

El orden fijo ya incluye el `id` como desempate, y como el `sort` del cliente se ignora, aquí no hace falta lista de campos permitidos.

## La respuesta: `SliceResponse`

`PagedModel` solo acepta un `Page`. Un `Slice` devuelto tal cual saldría con la estructura interna de la clase de Spring Data, el mismo problema que con `PageImpl`, y la propiedad `serialization-mode: via-dto` no lo convierte. El ejemplo usa un `record` propio:

```java
// Spring Data no trae un formato estable para Slice (PagedModel solo acepta Page)
public record SliceResponse<T>(List<T> content, int number, int size, boolean hasNext) {

    public static <T> SliceResponse<T> from(Slice<T> slice) {
        return new SliceResponse<>(slice.getContent(), slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
```

```java
@GetMapping("/tasks/recientes")
public SliceResponse<Task> findRecientes(Pageable pageable) {
    return SliceResponse.from(taskService.findRecientes(pageable));
}
```

Las dos respuestas, lado a lado (`content` abreviado):

```json title="GET /tasks?size=3"
{
  "content": [ "…3 tareas…" ],
  "page": {
    "size": 3,
    "number": 0,
    "totalElements": 60,
    "totalPages": 20
  }
}
```

```json title="GET /tasks/recientes?size=3"
{
  "content": [ "…3 tareas…" ],
  "number": 0,
  "size": 3,
  "hasNext": true
}
```

En la última página, `hasNext` pasa a `false`: es la señal para que el cliente deje de pedir.

## Cuándo usar cada uno

| | `Page` | `Slice` |
|---|---|---|
| Consultas por página | 2 (filas + `COUNT`); 1 si trae filas pero no se llena | 1 |
| Sabe el total | Sí | No, solo si hay siguiente |
| Encaja con | Un paginador con números ("página 3 de 12"), "60 resultados" | "Cargar más", *scroll* infinito, *feeds* |

Los dos tienen un límite en común: usan `OFFSET`. Para servir la página 10 000, la base de datos tiene que saltarse las filas de las 9 999 anteriores. Y si se insertan filas mientras alguien va pasando páginas, todo se desplaza y algunas se repiten. La solución para eso es la paginación por cursor (*keyset*): en vez de "sáltate N filas", la petición dice "las siguientes a esta". Spring Data la ofrece con `Window` y `ScrollPosition`, pero queda fuera de esta sección.
