---
title: Testing
---

# Testing

Hasta ahora, casi todos los tests de este sitio pasaban por un contexto Spring completo: `@SpringBootTest`, MockMvc, una base de datos (H2). Las excepciones eran clases sin dependencias que se podían crear con un simple `new`, como `JwtServiceTest` en la Fase 3. `@SpringBootTest` es una herramienta sólida, pero no la única — y usarla para todo tiene un costo que a veces no hace falta pagar. Esta fase enseña cuándo eso es demasiado (un test unitario con Mockito basta y sobra) y cuándo es demasiado poco (Testcontainers, para confirmar contra infraestructura real).

## Contenido

1. [JUnit avanzado](./junit-avanzado)
2. [Mockito y tests unitarios](./mockito-y-tests-unitarios)
3. [Testcontainers](./testcontainers)
4. [Cuándo usar cada uno](./cuando-usar-cada-uno)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/06-testing`](https://github.com/HarolRiosDev/spring-boot-docs/tree/main/examples/06-testing): el mismo API de gestión de tareas con auth JWT y caché de la Fase 4, portado sin cambios de diseño — el foco aquí es cómo se prueba, no qué se prueba.

```bash
cd examples/06-testing
./mvnw test     # unitarios + integración con H2, sin Docker
./mvnw verify   # además TaskApiIT con Testcontainers (necesita Docker)
```
