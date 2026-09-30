package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskUpdateRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Comprueba lo que hay de verdad en las tablas heredadas (con JdbcClient), no solo lo que devuelve la API
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LegacySchemaMappingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    private JsonNode createTask(boolean completada) throws Exception {
        String body = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskRequest("Revisar el mapeo", null, completada))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private <T> T column(String column, Class<T> type, long id) {
        return jdbcClient.sql("SELECT " + column + " FROM TB_TAREA WHERE ID_TAREA = :id")
                .param("id", id)
                .query(type)
                .single();
    }

    @Test
    void createdTask_takesItsIdFromTheLegacySequence() throws Exception {
        long id = createTask(false).get("id").asLong();

        assertThat(id).isGreaterThanOrEqualTo(1000);
        long lastValue = jdbcClient.sql("SELECT last_value FROM SQ_TAREA").query(Long.class).single();
        assertThat(id).isLessThanOrEqualTo(lastValue);
    }

    @Test
    void completada_isStoredAsSOrN() throws Exception {
        long completedId = createTask(true).get("id").asLong();
        long pendingId = createTask(false).get("id").asLong();

        assertThat(column("FL_COMPLETADA", String.class, completedId)).isEqualTo("S");
        assertThat(column("FL_COMPLETADA", String.class, pendingId)).isEqualTo("N");
    }

    @Test
    void fechaAlta_isFilledByTheDatabase_andReturnedWithoutReloading() throws Exception {
        JsonNode task = createTask(false);

        LocalDateTime inDatabase = column("FH_ALTA", LocalDateTime.class, task.get("id").asLong());
        assertThat(LocalDateTime.parse(task.get("fechaAlta").asText())).isEqualTo(inDatabase);
        assertThat(task.get("fechaModificacion").isNull()).isTrue();
    }

    @Test
    void update_triggerFillsFechaModificacion_andVersionGoesUpOnce() throws Exception {
        long id = createTask(false).get("id").asLong();

        String body = mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskUpdateRequest("Revisar el mapeo", null, true, 0))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode updated = objectMapper.readTree(body);

        LocalDateTime inDatabase = column("FH_MODIFICACION", LocalDateTime.class, id);
        assertThat(inDatabase).isNotNull();
        assertThat(LocalDateTime.parse(updated.get("fechaModificacion").asText())).isEqualTo(inDatabase);
        // Hibernate escribe version + 1 y el trigger calcula lo mismo: una sola subida, no dos
        assertThat(updated.get("version").asInt()).isEqualTo(1);
        assertThat(column("NU_VERSION", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void rowInsertedByTheOtherApp_isReadable_andSharesTheSequence() throws Exception {
        // La otra aplicación solo da el título: id, bandera, fecha y versión los pone la BD
        long theirId = jdbcClient.sql("INSERT INTO TB_TAREA (DS_TITULO) VALUES ('De la otra aplicación') RETURNING ID_TAREA")
                .query(Long.class)
                .single();

        mockMvc.perform(get("/tasks/{id}", theirId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("De la otra aplicación"))
                .andExpect(jsonPath("$.completada").value(false))
                .andExpect(jsonPath("$.fechaAlta").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0));

        long ourId = createTask(false).get("id").asLong();
        assertThat(ourId).isGreaterThan(theirId);
    }

    @Test
    void flywayHistory_startsWithTheBaselineOfTheLegacySchema() {
        List<String> history = jdbcClient.sql(
                        "SELECT version || ' ' || type FROM flyway_schema_history ORDER BY installed_rank")
                .query(String.class)
                .list();

        assertThat(history).containsExactly("1 BASELINE", "2 SQL");
    }
}
