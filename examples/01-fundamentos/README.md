# 01-fundamentos

API REST de gestión de tareas — ejemplo ejecutable de la Fase 1 (Fundamentos) del sitio **Spring Boot desde cero**. Demuestra inyección de dependencias, controladores REST, capas (controller/service/repository), validación y manejo de errores. Sin base de datos real: el repositorio es en memoria.

## Requisitos

- JDK 21 o superior.

## Ejecutar

```bash
./mvnw spring-boot:run
```

## Probar

```bash
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d '{"titulo": "Comprar leche", "descripcion": "2 litros", "completada": false}'

curl http://localhost:8080/tasks
```

Las tareas viven en memoria: se pierden al parar la aplicación.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/tasks` | Crear una tarea |
| GET | `/tasks` | Listar todas las tareas |
| GET | `/tasks/{id}` | Obtener una tarea |
| PUT | `/tasks/{id}` | Actualizar una tarea |
| DELETE | `/tasks/{id}` | Eliminar una tarea |

## Tests

```bash
./mvnw test
```
