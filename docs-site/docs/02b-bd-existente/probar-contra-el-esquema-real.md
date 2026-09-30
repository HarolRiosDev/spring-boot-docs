---
title: Probar contra el esquema real
sidebar_position: 5
---

# Probar contra el esquema real

En la [Fase 2](/docs/02-persistencia/probar-con-datos-reales) los tests usaban H2 en memoria: las migraciones de Flyway eran SQL estándar y H2 las entendía. Aquí eso ya no vale.

## Por qué aquí H2 no vale

El esquema heredado está escrito para Postgres, no para "SQL en general":

- El trigger y la función de purga están en **PL/pgSQL**, que H2 no ejecuta.
- `CHAR(1)`, las secuencias y el plegado de nombres a minúsculas se comportan distinto en cada motor.

Se podría escribir un segundo script "versión H2" del esquema, pero entonces los tests probarían ese segundo script, no el real, y las dos versiones acabarían siendo distintas sin que nadie lo notara. Con una base de datos heredada, la regla es simple: **los tests corren contra el mismo esquema, en el mismo motor**.

## El mismo script en los tests

Los tests arrancan un Postgres real con Testcontainers y le dan el mismo `docker/legacy-schema.sql` que usa `docker-compose.yml`:

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// El mismo script del DBA que usa docker-compose.yml: Postgres lo ejecuta al crear el contenedor
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
				.withCopyFileToContainer(
						MountableFile.forHostPath("docker/legacy-schema.sql", 0644),
						"/docker-entrypoint-initdb.d/legacy-schema.sql");
	}

}
```

- `withCopyFileToContainer` copia el script a `/docker-entrypoint-initdb.d/`, la carpeta que la imagen oficial de Postgres ejecuta con `psql` al crear la base de datos: exactamente lo que pasa con `docker compose up`. (`withInitScript` también ejecuta un script, pero lo parte en sentencias por su cuenta, y un script con bloques `$$ ... $$` de PL/pgSQL es justo donde eso puede fallar.)
- `@ServiceConnection` apunta el `DataSource` de la aplicación al contenedor, con su puerto aleatorio, sin configurar nada a mano.
- La clase es `public` porque la importan tests de otros paquetes (`controller/`).

Cada test la importa:

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TaskConcurrencyIT {
    // ...
}
```

Como todas las clases de test usan exactamente la misma configuración, Spring reutiliza un único contexto entre ellas y el contenedor arranca una sola vez. Cualquier diferencia rompe ese reparto: unas `properties` o un `@MockitoBean` propios, pero también una anotación de test distinta. Por eso `TasksLegacyApplicationIT` lleva `@AutoConfigureMockMvc` aunque no use MockMvc: sin ella tendría su propio contexto y, con él, un segundo contenedor.

## `*Test` sin Docker, `*IT` con Docker

Los tests que necesitan Postgres terminan en `IT` y los ejecuta Failsafe; los que no, terminan en `Test` y los ejecuta Surefire. Es la misma convención que explica la [Fase 6](/docs/06-testing/testcontainers):

```bash
./mvnw test     # solo SiNoConverterTest: no necesita Docker
./mvnw verify   # todos: los *IT levantan un Postgres con Testcontainers
```

Aquí casi todo es `*IT`: lo único que se puede probar sin base de datos es el convertidor `'S'`/`'N'`.

## Tests sobre una base de datos compartida

La base de datos de los tests trae los datos semilla del DBA y la comparten todas las clases. Tres reglas para que los tests no dependan unos de otros:

- **Cada test crea sus propios datos** y busca los suyos por id. Nada de "la lista tiene 6 elementos": otro test puede haber añadido o purgado tareas.
- **Sin `@Transactional` en los tests.** Con un test transaccional, el test y todas sus peticiones de MockMvc (que corren en el mismo hilo) comparten una sola transacción y un solo contexto de persistencia: un `PUT` reutilizaría la tarea que JPA ya tiene en memoria en vez de leer la fila, y el SQL de "la otra aplicación" correría dentro de nuestra propia transacción. Para probar la convivencia, cada escritura tiene que confirmarse como en producción.
- **"Hoy" lo decide la base de datos.** El test del informe pregunta `SELECT current_date` en vez de usar `LocalDate.now()`, para no depender de la zona horaria de la JVM.

Para simular a "la otra aplicación", el test escribe con SQL directo, igual que ella, sin pasar por la API:

```java
private void otherAppRenames(long id, String titulo) {
    jdbcClient.sql("UPDATE TB_TAREA SET DS_TITULO = :titulo WHERE ID_TAREA = :id")
            .param("titulo", titulo)
            .param("id", id)
            .update();
}

@Test
void updateWithStaleVersion_afterOtherAppChange_returnsConflict_andKeepsTheirChange() throws Exception {
    long id = createTask(); // la leemos en la versión 0
    otherAppRenames(id, "Cambiada por la otra aplicación");

    mockMvc.perform(put("/tasks/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new TaskUpdateRequest("Nuestra versión", null, false, 0))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));

    assertThat(tituloInDatabase(id)).isEqualTo("Cambiada por la otra aplicación");
}
```

`LegacySchemaMappingIT` usa el mismo recurso para comprobar lo que hay de verdad en las tablas: que `FL_COMPLETADA` guarda `'S'`, que el id sale de `SQ_TAREA`, que el historial de Flyway empieza con el baseline.

## Arrancar la aplicación sin `docker compose`

Initializr genera una clase `TestTasksLegacyApplication` en los tests, que arranca la aplicación con esa misma `TestcontainersConfiguration`:

```bash
./mvnw spring-boot:test-run
```

La aplicación arranca contra un Postgres efímero con el esquema heredado y sus datos semilla, sin `docker-compose.yml`. Al pararla, el contenedor desaparece.
