---
title: Cuándo usar cada uno
sidebar_position: 4
---

# Cuándo usar cada uno

Esta fase mostró tres estilos de test con código real del mismo proyecto. Ninguno reemplaza a los otros dos — cada uno responde una pregunta distinta.

| Estilo | Pregunta que responde | Costo | Ejemplo de esta fase |
|---|---|---|---|
| **Unitario (Mockito)** | ¿Esta lógica, aislada de todo lo demás, hace lo correcto? | Milisegundos, sin Spring, sin base de datos | `TaskServiceImplTest` — ownership sin contexto |
| **Integración (`@SpringBootTest` + H2)** | ¿Las piezas están bien conectadas — seguridad, serialización, consultas? | Segundos, contexto Spring completo, base de datos en memoria | Todos los `*ControllerTest` desde la Fase 1 |
| **Testcontainers** | ¿Funciona de verdad contra la infraestructura real (Postgres, Redis), no una aproximación? | Más lento, necesita Docker | `TaskApiIT` — Postgres y Redis reales |

## Una guía rápida

- Si la clase tiene lógica de negocio no trivial y sus dependencias son fáciles de sustituir (interfaces, sin `final`): **unitario**. Es el más barato y el más específico — cuando falla, casi siempre señala exactamente qué está mal.
- Si lo que hay que confirmar es que Spring conecta todo correctamente — una ruta protegida de verdad rechaza sin token, una consulta JPA devuelve lo que promete, la validación de un DTO se dispara: **integración**. No se puede mockear la configuración misma.
- Si el comportamiento depende de una característica real de la infraestructura que H2/`ConcurrentMapCacheManager` no reproducen fielmente — un tipo de columna específico de Postgres, la serialización real contra Redis, un índice o una restricción que H2 no aplica igual: **Testcontainers**. Es el único de los tres que no aproxima nada.

## Ninguno es gratis

Escribir solo tests unitarios de todo deja huecos: nada confirma que las piezas encajan. Escribir solo tests de integración para todo funciona, pero es lento y, cuando algo falla, hay que investigar entre muchas capas para encontrar la causa. Escribir Testcontainers para todo es correcto pero carísimo — nadie quiere esperar a que arranque un contenedor Docker para probar una validación de tres líneas. La combinación de los tres, cada uno donde responde mejor su pregunta, es lo que da cobertura real sin que la suite de tests se vuelva insoportable de correr.
