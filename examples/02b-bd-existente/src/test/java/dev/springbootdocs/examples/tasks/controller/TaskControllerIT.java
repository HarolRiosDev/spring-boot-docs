package dev.springbootdocs.examples.tasks.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long createTask(String titulo) throws Exception {
        String body = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(titulo, "desde el test", false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createTask_returnsCreatedWithVersionZero() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Nueva", null, false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.titulo").value("Nueva"))
                .andExpect(jsonPath("$.completada").value(false))
                .andExpect(jsonPath("$.fechaAlta").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createTask_withBlankTitulo_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("", null, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.titulo").exists());
    }

    @Test
    void createTask_withTituloLongerThanTheColumn_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("x".repeat(256), null, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.titulo").exists());
    }

    @Test
    void listTasks_includesCreatedTask() throws Exception {
        long id = createTask("Para la lista");

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].titulo").value("Para la lista"));
    }

    @Test
    void getTask_whenMissing_returnsNotFound() throws Exception {
        mockMvc.perform(get("/tasks/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateTask_withCurrentVersion_returnsUpdatedTask() throws Exception {
        long id = createTask("Antes");

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskUpdateRequest("Después", null, true, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Después"))
                .andExpect(jsonPath("$.completada").value(true))
                .andExpect(jsonPath("$.fechaModificacion").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void updateTask_whenMissing_returnsNotFound() throws Exception {
        mockMvc.perform(put("/tasks/{id}", 999_999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskUpdateRequest("Nadie", null, false, 0))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTask_withoutVersion_returnsBadRequest() throws Exception {
        long id = createTask("Sin versión");

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskUpdateRequest("Otra", null, false, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());
    }

    @Test
    void deleteTask_returnsNoContent_andThenNotFound() throws Exception {
        long id = createTask("Para borrar");

        mockMvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound());
    }
}
