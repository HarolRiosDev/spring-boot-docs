---
title: Bienvenida
---

# Spring Boot desde cero

Este sitio te enseña Spring Boot de forma práctica: cada tema tiene su documentación en español y, casi siempre, un **proyecto Spring Boot ejecutable** que puedes clonar y correr en tu máquina.

## ¿Para quién es esto?

Está pensado para alguien que **ya sabe Java** (POO, colecciones, algo de lambdas/streams) pero es **nuevo en Spring Boot**. Si necesitas repasar Java primero, hay una sección opcional para eso.

## Cómo está organizado

- 📖 La documentación de cada tema vive en este sitio, en el menú de la izquierda.
- 💻 Los ejemplos ejecutables viven en la carpeta [`examples/`](https://github.com/HarolRiosDev/spring-boot-docs/tree/main/examples) del repositorio — un proyecto Maven independiente por tema, que se compila y ejecuta por sí solo sin depender de los demás.

## Ruta sugerida

| Fase | Tema | Proyecto ejecutable |
|---|---|---|
| ☕ Repaso (opcional) | POO, herencia, polimorfismo, interfaces, colecciones, lambdas/streams | — |
| 1 | Fundamentos: DI, beans, REST, capas, validación, manejo de errores | `01-fundamentos` |
| 2 | Persistencia: Spring Data JPA, Postgres, Flyway | `02-persistencia` |
| 3 | Seguridad y Auth: Spring Security, JWT, roles | `03-security-jwt` |
| 4 | Caché y Redis: Spring Cache, Redis, invalidación | `04-cache-redis` |
| 5 | Mensajería: Kafka, productores/consumidores, notificaciones | `05-kafka` |
| 6 | Testing: JUnit, Mockito, Testcontainers | `06-testing` |
| 7 | Producción: Actuator, métricas, logging, trazas, OpenAPI | `07-observabilidad` |

Cada sección todavía en construcción lo indica claramente — el índice completo ya está aquí para que veas el mapa completo desde el día uno.

Empieza por **Fundamentos** en el menú, o por el **Repaso de Java** si quieres afianzar la base primero.
