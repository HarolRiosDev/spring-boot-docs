---
title: Security/JWT
---

# Security/JWT

Hasta ahora, cualquiera podía llamar al API de tareas de las Fases 1-2 sin identificarse. Esta fase añade autenticación real: cada usuario se registra, inicia sesión y recibe un JWT propio (emitido por la propia app, sin depender de un proveedor externo como Keycloak o Auth0) que debe enviar en cada petición. Además, cada tarea pasa a tener un dueño, y aparece un segundo rol (`ADMIN`) con permisos ampliados.

## Contenido

1. [Spring Security básico](./spring-security-basico)
2. [Autenticación con JWT](./autenticacion-jwt)
3. [Roles y autorización](./roles-y-autorizacion)
4. [Probar endpoints protegidos](./probar-endpoints-protegidos)

## Ejemplo ejecutable

Todo el código de esta fase vive en [`examples/03-security-jwt`](https://github.com/HarolRiosDev/spring-boot-docs/tree/main/examples/03-security-jwt): la misma API de tareas de la Fase 2, ahora protegida.

```bash
cd examples/03-security-jwt
docker compose up -d
./mvnw spring-boot:run
```
