package dev.springbootdocs.examples.tasks;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String validRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new TaskRequest("Comprar leche", "2 litros", false));
    }

    private Long createTaskAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createTask_returnsCreated() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.titulo").value("Comprar leche"))
                .andExpect(jsonPath("$.completada").value(false));
    }

    @Test
    void createTask_withBlankTitulo_returnsBadRequest() throws Exception {
        String json = objectMapper.writeValueAsString(new TaskRequest("", "sin título", false));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.titulo").exists());
    }

    @Test
    void listTasks_includesCreatedTask() throws Exception {
        createTaskAndGetId();

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getTaskById_whenExists_returnsTask() throws Exception {
        Long id = createTaskAndGetId();

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.titulo").value("Comprar leche"));
    }

    @Test
    void getTaskById_whenNotFound_returns404() throws Exception {
        mockMvc.perform(get("/tasks/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateTask_whenExists_returnsUpdatedTask() throws Exception {
        Long id = createTaskAndGetId();
        String updateJson = objectMapper.writeValueAsString(
                new TaskRequest("Comprar pan", "integral", true));

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Comprar pan"))
                .andExpect(jsonPath("$.completada").value(true));
    }

    @Test
    void updateTask_whenNotFound_returns404() throws Exception {
        String updateJson = objectMapper.writeValueAsString(
                new TaskRequest("No existe", null, false));

        mockMvc.perform(put("/tasks/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_whenExists_thenReturns404OnFollowUpGet() throws Exception {
        Long id = createTaskAndGetId();

        mockMvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_whenNotFound_returns404() throws Exception {
        mockMvc.perform(delete("/tasks/{id}", 999999L))
                .andExpect(status().isNotFound());
    }
}
