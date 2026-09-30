package dev.springbootdocs.examples.tasks.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.TestcontainersConfiguration;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.dto.TaskUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

// "La otra aplicación" se simula con SQL directo que, como ella, no sabe que NU_VERSION existe
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TaskConcurrencyIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    private long createTask() throws Exception {
        String body = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Compartida", null, false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void otherAppRenames(long id, String titulo) {
        jdbcClient.sql("UPDATE TB_TAREA SET DS_TITULO = :titulo WHERE ID_TAREA = :id")
                .param("titulo", titulo)
                .param("id", id)
                .update();
    }

    private String tituloInDatabase(long id) {
        return jdbcClient.sql("SELECT DS_TITULO FROM TB_TAREA WHERE ID_TAREA = :id")
                .param("id", id)
                .query(String.class)
                .single();
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

    @Test
    void updateWithCurrentVersion_afterOtherAppChange_succeeds() throws Exception {
        long id = createTask();
        otherAppRenames(id, "Cambiada por la otra aplicación"); // el trigger sube NU_VERSION a 1

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskUpdateRequest("Tras releerla", null, false, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Tras releerla"))
                .andExpect(jsonPath("$.version").value(2));
    }
}
