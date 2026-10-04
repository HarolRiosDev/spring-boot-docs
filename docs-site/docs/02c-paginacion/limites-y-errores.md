---
title: Límites y errores
sidebar_position: 2
---

# Límites y errores

Un listado paginado recibe parámetros del cliente, y el cliente puede mandar cualquier cosa: una página enorme, un número negativo, un campo que no existe. Unas cosas las resuelve Spring por su cuenta; otras las tiene que resolver la aplicación.

## Lo que Spring resuelve sin decir nada

| Petición | Respuesta |
|---|---|
| `?size=5000` | 200, con páginas de 100 (`"size": 100`) |
| `?size=0`, `?size=-3`, `?size=abc` | 200, con el tamaño por defecto (20) |
| `?page=-1`, `?page=abc` | 200, con la primera página |
| `?page=999` | 200, con `"content": []` y los totales correctos |

Ninguno de estos casos es un error. Un número de página o un tamaño imposibles se cambian por el valor por defecto, y pedir una página que no existe devuelve una página vacía:

```json
{
  "content": [],
  "page": {
    "size": 20,
    "number": 999,
    "totalElements": 60,
    "totalPages": 3
  }
}
```

### Una página demasiado lejana

Hay un caso que Spring no resuelve por su cuenta. Spring Data calcula el desplazamiento de la página (`page × size`) y no admite que pase de `Integer.MAX_VALUE` (2 147 483 647). Con `?page=99999999&size=100`, el repositorio lanzaría `InvalidDataAccessApiUsageException: Page offset exceeds Integer.MAX_VALUE (2147483647)`, y la respuesta sería un 500. El servicio lo comprueba antes de llamar al repositorio y devuelve una página vacía, igual que con `?page=999`:

```java
// Spring Data no admite desplazamientos (page * size) que no quepan en un int y lanzaría
// una excepción, un 500. Una página tan lejana está vacía, igual que ?page=999
private static boolean fueraDeRango(Pageable pageable) {
    return pageable.getOffset() > Integer.MAX_VALUE;
}
```

```java
Pageable pagina = conDesempate(pageable);
if (fueraDeRango(pagina)) {
    return new PageImpl<>(List.of(), pagina, taskRepository.count(spec));
}
return taskRepository.findAll(spec, pagina);
```

`PageImpl` es la clase de Spring Data que implementa `Page`; aquí se construye a mano, sin contenido y con el total real (el `count` respeta los filtros). `/tasks/recientes` hace lo mismo con `SliceImpl` y `hasNext` a `false`.

### El tamaño máximo: `max-page-size`

El caso que más importa es el primero. Sin límite, un cliente puede pedir `?size=1000000` y volver a tener el problema que la paginación venía a resolver. Spring trae un máximo de 2000, que para la mayoría de las APIs sigue siendo demasiado. El ejemplo lo baja a 100:

```yaml
spring:
  data:
    web:
      pageable:
        # Un cliente que pida size=5000 recibe páginas de 100, sin error
        max-page-size: 100
```

¿Por qué recortar en vez de responder un 400? Porque el cliente sigue funcionando: recibe menos de lo que pidió, pero lo ve en `"size": 100` y puede seguir pidiendo páginas. Si en tu API prefieres un error, tendrías que validar el parámetro tú mismo, pero el recorte es lo habitual.

## Ordenar por un campo que no existe

Sin ninguna protección, `?sort=noexiste` acaba así:

```json
{
  "timestamp": "2026-10-04T14:54:20.322Z",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/tasks"
}
```

Y en el log:

```
org.springframework.data.core.PropertyReferenceException: No property 'noexiste' found for type 'Task'
```

Spring no comprueba el campo al construir el `Pageable`, porque en ese momento no sabe a qué entidad va. Lo descubre Spring Data al construir la consulta, ya dentro del repositorio. Y un 500 aquí miente: el servidor no ha fallado, la petición está mal.

### Una lista de campos permitidos

El servicio comprueba cada campo del orden antes de llamar al repositorio:

```java
// Campos por los que el cliente puede ordenar. Una List y no un Set.of,
// porque el mensaje de error los enumera y Set.of no garantiza el orden
private static final List<String> CAMPOS_ORDENABLES = List.of("fechaCreacion", "titulo", "completada", "id");

private void validarOrden(Sort sort) {
    for (Sort.Order order : sort) {
        if (!CAMPOS_ORDENABLES.contains(order.getProperty())) {
            throw new InvalidSortException(order.getProperty(), CAMPOS_ORDENABLES);
        }
    }
}
```

`InvalidSortException` lleva el mensaje, y `GlobalExceptionHandler` la convierte en el mismo `ApiError` que el resto de errores de la API:

```java
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String campo, List<String> permitidos) {
        super("No se puede ordenar por '" + campo + "'. Campos permitidos: " + String.join(", ", permitidos));
    }
}
```

```java
// Un sort por un campo que no está en la lista de campos ordenables
@ExceptionHandler(InvalidSortException.class)
public ResponseEntity<ApiError> handleInvalidSort(InvalidSortException ex) {
    ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}
```

```json
{
  "status": 400,
  "message": "No se puede ordenar por 'noexiste'. Campos permitidos: fechaCreacion, titulo, completada, id",
  "timestamp": "2026-10-04T14:54:14.134697200Z"
}
```

Se podría capturar `PropertyReferenceException` y ya está, pero una lista de campos permitidos protege de más cosas:

- **Campos que existen pero no conviene ofrecer.** `?sort=descripcion` también da 400, aunque `descripcion` existe. Muchas tareas no tienen descripción, y PostgreSQL y H2 colocan los `NULL` en sitios distintos (PostgreSQL al final en orden ascendente; H2, al principio): la misma petición daría páginas distintas según la base de datos.
- **Cada campo ordenable es un compromiso.** Con muchas filas, cada orden que admites debería tener su índice. La lista deja por escrito cuáles son.
- **Direcciones mal escritas.** Spring toma lo que no reconoce como `asc` o `desc` por otro campo: `?sort=titulo,sideways` es "ordena por `titulo` y luego por `sideways`". Con la lista, la respuesta es un 400 que dice exactamente qué sobra: `No se puede ordenar por 'sideways'…`.

La comprobación distingue mayúsculas (`?sort=FechaCreacion` también es un 400), igual que Spring. Lo que sí entiende Spring es un tercer elemento, `ignorecase`: `?sort=titulo,desc,ignorecase` ordena por `lower(titulo)`. En campos que no son texto, como `completada` o `id`, simplemente lo ignora.

¿Y si mañana alguien renombra `titulo` en la entidad y se olvida de la lista? Entonces `?sort=titulo` volvería a dar un 500, y sería correcto: es un error del código, no de quien hace la petición.

## Un parámetro con un tipo que no encaja

Con los [filtros](./filtros) de la página siguiente, `?completada=quizas` no se puede convertir en un `Boolean`. Spring lanza `MethodArgumentTypeMismatchException` y, por defecto, responde 400 con su propio formato de error, distinto del `ApiError` del resto de la API. Lo mismo pasa con `GET /tasks/abc`, donde `abc` no es un `Long`. Un handler más lo deja todo con el mismo formato:

```java
// Un parámetro de la URL que no se puede convertir a su tipo: ?completada=quizas o /tasks/abc
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(),
            "El parámetro '" + ex.getName() + "' tiene un valor no válido: '" + ex.getValue() + "'");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}
```

```json
{
  "status": 400,
  "message": "El parámetro 'completada' tiene un valor no válido: 'quizas'",
  "timestamp": "2026-10-04T14:54:16.312685200Z"
}
```
