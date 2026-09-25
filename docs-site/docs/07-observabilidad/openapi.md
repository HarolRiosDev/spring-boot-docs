---
title: OpenAPI con springdoc
sidebar_position: 4
---

# OpenAPI con springdoc

**OpenAPI** es el formato estándar para describir un API REST: rutas, parámetros, cuerpos, respuestas y seguridad, en un JSON o YAML que leen tanto las personas como las herramientas. A partir de ese documento se genera documentación navegable, clientes en otros lenguajes o tests de contrato. **springdoc** lo genera solo, leyendo los controllers de la aplicación.

## Sin escribir nada

Basta con la dependencia:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.0</version>
</dependency>
```

springdoc no forma parte de Spring Boot, así que su versión se escribe a mano. Con la dependencia aparecen dos cosas:

- `http://localhost:8080/v3/api-docs`: la especificación OpenAPI, en JSON.
- `http://localhost:8080/swagger-ui.html`: **Swagger UI**, una página que la muestra y permite probar cada endpoint desde el navegador.

springdoc saca casi todo del propio código: las rutas y métodos de los `@RestController`, los `@PathVariable`, y los DTO de entrada y salida como esquemas. Hasta las anotaciones de Bean Validation acaban en el documento: en `TaskRequest`, `@NotBlank` marca `titulo` como obligatorio y `@Size(max = 255)` se convierte en `maxLength: 255`:

```json
"TaskRequest": {
  "type": "object",
  "properties": {
    "titulo": { "type": "string", "maxLength": 255, "minLength": 0 },
    "descripcion": { "type": "string", "maxLength": 1000, "minLength": 0 },
    "completada": { "type": "boolean" }
  },
  "required": ["titulo"]
}
```

## Un poco de contexto con anotaciones

Lo que el código no dice se añade con unas pocas anotaciones:

```java
@RestController
@Tag(name = "Tareas", description = "CRUD de tareas: cada usuario ve las suyas; un ADMIN, todas")
public class TaskController {

    @PostMapping("/tasks")
    @Operation(summary = "Crear una tarea")
    public ResponseEntity<TaskResponse> create(...) { ... }
```

`@Tag` agrupa los endpoints de un controller en una sección de Swagger UI; `@Operation(summary = ...)` da a cada uno una frase legible. No hace falta más: cuanto más se anota, más texto hay que mantener al día con el código.

## Probar endpoints protegidos desde Swagger UI

Casi todo el API pide un JWT. Para que Swagger UI pueda enviarlo, la especificación tiene que declarar cómo se autentica el API:

```java
@Bean
public OpenAPI tasksOpenApi() {
    return new OpenAPI()
            .info(new Info()
                    .title("Tasks API")
                    .version("v1")
                    .description("..."))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
}
```

El `SecurityScheme` dice "un token en la cabecera `Authorization: Bearer ...`", y el `SecurityRequirement` global dice "todos los endpoints lo necesitan". Login y registro son la excepción, y un `@SecurityRequirements` vacío en `AuthController` anula el requisito para ellos:

```java
@RestController
@Tag(name = "Autenticación", description = "Registro y login; devuelven el JWT para el resto de endpoints")
@SecurityRequirements
public class AuthController {
```

En Swagger UI:

1. Abre `POST /auth/login`, pulsa **Try it out** y envía `{"username": "admin", "password": "admin12345"}`.
2. Copia el `token` de la respuesta.
3. Pulsa **Authorize**, arriba a la derecha, y pégalo.
4. Desde ese momento, cada petición que lances desde la página lleva el token.

## Swagger UI no es para producción

`/v3/api-docs` y Swagger UI son públicos (`permitAll()` en `SecurityConfig`): describen el API, pero no dan acceso a nada que no dé ya el propio API. Aun así, en producción el ejemplo apaga la interfaz interactiva:

```yaml
springdoc:
  swagger-ui:
    enabled: false
```

El JSON de `/v3/api-docs` se queda: otros equipos lo usan para generar clientes o para ver qué ha cambiado entre versiones. Si el API es interno y no quieres publicar ni su descripción, `springdoc.api-docs.enabled: false` apaga también el JSON.

:::caution[Un 404 que llega como 401]
Con Swagger UI apagado, `/swagger-ui.html` debería responder 404. Sin un ajuste en la seguridad, un servidor real responde **401 "No autenticado"**, y el motivo no tiene nada que ver con springdoc. Cuando una petición termina en 404 o en 500, el servidor la reenvía internamente a la página de error de Spring Boot, `/error`, y ese reenvío vuelve a pasar por Spring Security. Si `/error` no está permitida, una petición sin token recibe un 401 en lugar del error real, lo que confunde al cliente y ensucia el panel de errores de Grafana. La solución es una línea en `SecurityConfig`:

```java
.requestMatchers("/error").permitAll()
```

No abre nada: las rutas protegidas se siguen rechazando antes de llegar a ningún controller. Los tests con MockMvc no lo detectan, porque MockMvc no hace ese reenvío; `ErrorPageSecurityTest` lo prueba con un servidor real.
:::

## El caso contrario: escribir la especificación a mano

springdoc sigue el enfoque *code-first*: primero el código, y el documento sale de él. El enfoque contrario, *contract-first*, empieza por el documento: se diseña y se acuerda el API antes de escribir una línea, y el código se ajusta a él. Editar el documento a mano también sirve para retocar lo que genera springdoc antes de compartirlo.

Para eso puedes usar un editor de OpenAPI que funciona en el navegador, como [harolriosdev.github.io/openapi-editor](https://harolriosdev.github.io/openapi-editor/). Un buen punto de partida es el JSON que genera esta misma aplicación en `/v3/api-docs`.

## Cómo se prueba

`OpenApiDocsTest` pide `/v3/api-docs` sin token y comprueba lo que depende del código del ejemplo, no de springdoc: que están todas las rutas, que se declara el esquema `bearer` JWT como requisito global, que `/auth/login` lo anula con `"security": []` y que las operaciones llevan su `summary` y su tag. `ProdProfileTest` comprueba que con el perfil `prod` Swagger UI responde 404 y el JSON sigue en 200.
