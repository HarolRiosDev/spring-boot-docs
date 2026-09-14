---
title: Fundamentos
---

# Fundamentos

Esta fase cubre lo esencial de Spring Boot antes de tocar temas más avanzados: cómo se conectan los objetos entre sí, cómo exponer una API REST, cómo organizar el código en capas, cómo validar las entradas y cómo devolver errores útiles.

## Contenido

1. [Inyección de dependencias y beans](./inyeccion-dependencias)
2. [Controladores REST](./controladores-rest)
3. [Capas: controller → service → repository](./capas)
4. [Validación](./validacion)
5. [Manejo de errores](./manejo-errores)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/01-fundamentos`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples/01-fundamentos): una API REST de gestión de tareas (crear, listar, consultar, actualizar y eliminar), construida capa por capa según se explica en las páginas de arriba.

```bash
cd examples/01-fundamentos
./mvnw spring-boot:run
```
