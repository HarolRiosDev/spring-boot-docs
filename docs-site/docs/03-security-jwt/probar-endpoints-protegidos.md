---
title: Probar endpoints protegidos
sidebar_position: 4
---

# Probar endpoints protegidos

Con todo conectado, así es como se usa el API de principio a fin.

## Levantar la base de datos y la aplicación

```bash
docker compose up -d
cd examples/03-security-jwt
./mvnw spring-boot:run
```

Al arrancar, Flyway crea `users` y `tasks`, y siembra un usuario `admin` (contraseña `admin12345`) — ver `V3__seed_admin_user.sql`. Ese hash se generó una sola vez, offline, con `BCryptPasswordEncoder`; nunca se guarda una contraseña en texto plano ni siquiera en la migración.

## Registrar un usuario y obtener un token

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "ana", "password": "password123"}'
```

Responde `201 Created` con `{ "token": "eyJhbGciOi..." }`. Guarda ese valor:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "ana", "password": "password123"}' | jq -r .token)
```

## Usar el token

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"titulo": "Comprar leche", "descripcion": "2 litros", "completada": false}'

curl http://localhost:8080/tasks -H "Authorization: Bearer $TOKEN"
```

Sin el header `Authorization`, ambas peticiones devuelven `401`. Si intentas leer una tarea de otro usuario con tu token, obtienes `403` — pero como `admin`, la misma petición funciona contra cualquier tarea.

## Cómo prueban esto los tests, sin `@WithMockUser`

Los tests de `TaskControllerTest` no simulan la autenticación con anotaciones de test — obtienen un JWT real llamando a `/auth/register` y `/auth/login`, exactamente como haría un cliente real:

```java
private String registerAndLogin(String username) throws Exception {
    String password = "password123";
    mockMvc.perform(post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))));

    MvcResult result = mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    return objectMapper.readTree(body).get("token").asText();
}
```

La ventaja: si algo se rompe en el filtro JWT, en `SecurityConfig`, o en cómo se firma/valida el token, estos tests lo detectan — una autenticación simulada por el framework de test nunca pasa por ese código.

## Sin Docker

`./mvnw test` **no necesita Docker ni Postgres**. Igual que en la Fase 2, `src/test/resources/application.yml` apunta a H2 en modo compatibilidad PostgreSQL, y las mismas migraciones de Flyway (incluida la semilla del admin) se aplican también ahí — así que los tests de `AdminControllerTest` (login como `admin`/`admin12345`) comprueban de verdad que la migración `V3` sembró un hash válido, no uno que solo "parece" correcto.
