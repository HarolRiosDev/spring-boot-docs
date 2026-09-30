package dev.springbootdocs.examples.tasks.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.TestcontainersConfiguration;
import dev.springbootdocs.examples.tasks.dto.CommentRequest;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TaskReportControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    private long createTask(String titulo, boolean completada) throws Exception {
        String body = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(titulo, null, completada))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String addComment(long taskId, String texto) throws Exception {
        String body = mockMvc.perform(post("/tasks/{id}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest(texto))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("fechaAlta").asString();
    }

    private boolean taskExists(long id) {
        return jdbcClient.sql("SELECT COUNT(*) FROM TB_TAREA WHERE ID_TAREA = :id")
                .param("id", id)
                .query(Long.class)
                .single() == 1;
    }

    private long insertOldTask(String completada) {
        return jdbcClient.sql("""
                        INSERT INTO TB_TAREA (DS_TITULO, FL_COMPLETADA, FH_ALTA)
                        VALUES ('Creada hace 60 días', :completada, now() - INTERVAL '60 days')
                        RETURNING ID_TAREA
                        """)
                .param("completada", completada)
                .query(Long.class)
                .single();
    }

    @Test
    void summary_comesFromTheView_withCommentCountAndLastCommentDate() throws Exception {
        long id = createTask("Resumida", false);
        addComment(id, "Uno");
        String lastCommentDate = addComment(id, "Dos");

        mockMvc.perform(get("/tasks/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].titulo").value("Resumida"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].numComentarios").value(2))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].fechaUltimoComentario").value(lastCommentDate));
    }

    @Test
    void summary_ofTaskWithoutComments_hasZeroCommentsAndNoDate() throws Exception {
        long id = createTask("Sin comentarios", false);

        mockMvc.perform(get("/tasks/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].numComentarios").value(0))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].fechaUltimoComentario", hasItem(nullValue())));
    }

    @Test
    void tasksPerDay_countsTodaysTasks() throws Exception {
        createTask("Hoy 1", false);
        createTask("Hoy 2", true);
        // "Hoy" según la base de datos, no según la JVM: así no depende de la zona horaria
        LocalDate today = jdbcClient.sql("SELECT current_date").query(LocalDate.class).single();

        mockMvc.perform(get("/reports/tasks-per-day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.dia == '" + today + "')].creadas", hasItem(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[?(@.dia == '" + today + "')].completadas", hasItem(greaterThanOrEqualTo(1))));
    }

    @Test
    void purge_callsTheDatabaseFunction_andOnlyDeletesOldCompletedTasks() throws Exception {
        long oldCompleted = insertOldTask("S");
        long oldPending = insertOldTask("N");
        long recentCompleted = createTask("Completada hoy", true);

        mockMvc.perform(post("/tasks/purge").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purged").value(greaterThanOrEqualTo(1)));

        assertThat(taskExists(oldCompleted)).isFalse();
        assertThat(taskExists(oldPending)).isTrue();
        assertThat(taskExists(recentCompleted)).isTrue();
    }

    @Test
    void purge_withZeroDays_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tasks/purge").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.days").exists());
    }

    @Test
    void purge_withoutDays_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tasks/purge"))
                .andExpect(status().isBadRequest());
    }
}
