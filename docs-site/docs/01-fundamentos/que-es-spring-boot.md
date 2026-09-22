---
title: ¿Qué es Spring Boot?
sidebar_position: 1
---

# ¿Qué es Spring Boot?

## Framework vs. librería

Antes de tocar código, conviene distinguir dos términos que se usan a menudo sin pensar demasiado: **librería** y **framework**.

- Una **librería** es código que tú llamas cuando lo necesitas. Tú tienes el control: decides cuándo invocar cada función, y el flujo del programa lo escribes tú. Si usas una librería para parsear JSON, tu código llama a `objectMapper.readValue(...)` cuando le hace falta — el flujo sigue siendo tuyo.
- Un **framework** invierte esa relación (el llamado "principio de inversión de control"): es el framework quien llama a tu código, no al revés. Tú escribes piezas — un controlador, un servicio — y es el framework quien decide cuándo ejecutarlas: cuándo llega una petición HTTP, cuándo arranca la aplicación, etc. Spring Boot es un framework: no escribes un `main` que orquesta todo paso a paso, escribes componentes y es Spring Boot quien decide cómo y cuándo conectarlos.

## Qué es Spring Boot, concretamente

Spring Boot no es un lenguaje nuevo ni una plataforma aparte — es una capa sobre el **Spring Framework** (el framework Java original, de 2003, muy potente pero históricamente tedioso de configurar) que añade dos cosas:

1. **Autoconfiguración.** Spring Boot detecta qué hay en tu classpath (¿tienes `spring-boot-starter-data-jpa`? ¿el driver de Postgres?) y configura automáticamente los beans razonables para ese caso, sin que escribas XML ni clases de configuración a mano para los casos comunes. Es "convención sobre configuración": si sigues la convención, no hace falta configurar nada; si necesitas algo distinto, lo sobrescribes explícitamente.
2. **Empaquetado ejecutable.** Un proyecto Spring Boot se compila a un único `.jar` que incluye un servidor embebido (Tomcat por defecto) — `java -jar miapp.jar` y ya tienes un servidor HTTP corriendo, sin instalar ni configurar un servidor aparte.

En este sitio vas a ver constantemente el mismo patrón: "añade una dependencia (un *starter*) al `pom.xml`, y Spring Boot autoconfigura lo necesario" — es la idea central detrás de casi todo lo que se explica en las fases siguientes.

## API vs. librería

Otra distinción que aparece constantemente y conviene tener clara desde ahora, porque este sitio usa ambas palabras con su significado más habitual en un equipo backend:

- Una **librería** (o dependencia) es código reutilizable que **añades a tu propio proceso**: vive dentro de tu aplicación, se compila junto con tu código, y la usas llamando a sus clases y métodos directamente en Java. `jjwt` (que usarás en la Fase 3 para firmar JWT) es una librería — añades la dependencia al `pom.xml` y llamas a `Jwts.builder()...` desde tu propio código.
- Una **API** — en el sentido de "API HTTP" o "API REST", el que más vas a usar aquí — es un **contrato que expone un servicio para que otros programas lo llamen por red**, normalmente sobre HTTP, sin importar en qué lenguaje esté escrito el que llama. Cuando construyas un `@RestController` con Spring Boot (siguiente página), estarás construyendo una API: no expones tus clases Java directamente, expones rutas HTTP (`GET /tasks`, `POST /tasks`) que cualquier cliente puede llamar sin saber una sola línea de Java.

La confusión suele venir de que "API" también se usa, en un sentido más amplio, para referirse a la superficie pública de cualquier componente — en ese sentido, una librería también tiene su propia "API" (los métodos públicos que expone). Pero cuando alguien dice sin más contexto "la API" en el día a día de un backend, casi siempre se refiere a una API HTTP como la que construyes en la Fase 1: algo que corre como su propio servicio y se llama por red, no algo que importas y ejecutas en el mismo proceso.

| | Librería | API (HTTP) |
|---|---|---|
| ¿Dónde corre? | Dentro de tu propio proceso | En su propio servicio, por red |
| ¿Cómo se usa? | Import + llamada a métodos Java | Petición HTTP (`GET`, `POST`...) |
| ¿Mismo lenguaje en ambos lados? | Normalmente sí (o un puente) | No hace falta — cualquier cliente HTTP vale |
| Ejemplo en este sitio | `jjwt`, cualquier `spring-boot-starter-*` | La API de tareas que construyes en `examples/01-fundamentos` |
