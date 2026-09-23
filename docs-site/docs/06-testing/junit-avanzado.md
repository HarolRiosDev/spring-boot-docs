---
title: JUnit avanzado
sidebar_position: 1
---

# JUnit avanzado

Hasta ahora, cada caso de validación de `TaskRequest` vivía en su propio método `@Test`, casi idéntico al anterior salvo por el dato de entrada. Esta página cubre las herramientas de JUnit 5 para dejar de repetir esa estructura: tests parametrizados, agrupación con `@Nested`, y el ciclo de vida de una clase de test.

## `@ParameterizedTest`

`TaskRequest` valida tres cosas: `titulo` no puede estar en blanco, `titulo` no puede superar 255 caracteres, `descripcion` no puede superar 1000. Antes de esta fase, solo el segundo caso tenía test. En vez de escribir tres métodos casi idénticos, un único método parametrizado cubre varias entradas:

```java
@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {"   "})
void tituloEnBlanco_returnsBadRequest(String tituloInvalido) throws Exception {
    // el cuerpo del test recibe tituloInvalido: null, "", y "   " — tres ejecuciones
}
```

`@NullAndEmptySource` inyecta `null` y `""`; `@ValueSource` añade valores propios — aquí, un título que solo tiene espacios (que `@NotBlank` también rechaza, a diferencia de una simple comprobación de "no vacío"). JUnit ejecuta el método una vez por cada valor, cada una como un test independiente en el reporte — si una falla, se sabe exactamente cuál.

Para datos que no son un único valor por caso (por ejemplo, "esta entrada, más el campo que se espera que falle"), `@CsvSource` o `@MethodSource` permiten combinaciones más ricas — no se usan aquí porque `TaskRequest` no lo necesita, pero son la herramienta cuando un solo `@ValueSource` se queda corto.

## `@Nested` para agrupar por intención

```java
@Nested
class TituloInvalido {
    // tests sobre el campo titulo
}

@Nested
class DescripcionInvalida {
    // tests sobre el campo descripcion
}
```

Una clase interna no estática anotada `@Nested` agrupa tests relacionados bajo un mismo contexto — en el reporte aparecen anidados bajo el nombre de la clase (`TaskControllerTest > TituloInvalido > tituloEnBlanco_returnsBadRequest`), lo que hace más fácil ver de un vistazo qué se está probando cuando la clase de test crece. No cambia qué se ejecuta, solo cómo se organiza y se reporta.

## Ciclo de vida: `@BeforeEach` vs `@BeforeAll`

Un método `@BeforeEach` se ejecuta antes de **cada** test — es el que ya se viene usando implícitamente en este proyecto vía la inyección de `MockMvc`/`ObjectMapper` con `@Autowired` (Spring los reinyecta en cada instancia de test, una instancia por método por defecto). Un método `@BeforeAll` se ejecuta **una sola vez**, antes de todos los tests de la clase — debe ser `static` a menos que la clase use `@TestInstance(Lifecycle.PER_CLASS)`.

La diferencia importa para el costo y el aislamiento: algo que se pueda compartir sin efectos secundarios entre tests (por ejemplo, un contenedor de Testcontainers — ver [Testcontainers](./testcontainers)) va en `@BeforeAll`/un campo `static`, porque levantarlo una vez por clase es mucho más barato que una vez por test. Algo que un test pueda mutar y que otro test no deba heredar (como el estado de un mock) va en `@BeforeEach`.
