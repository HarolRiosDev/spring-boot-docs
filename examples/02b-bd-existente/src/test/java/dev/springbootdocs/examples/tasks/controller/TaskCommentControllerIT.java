package dev.springbootdocs.examples.tasks.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.TestcontainersConfiguration;
import dev.springbootdocs.examples.tasks.dto.CommentRequest;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TaskCommentControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private long createTask() throws Exception {
        String body = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Comentada", null, false))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private ResultActions addComment(long taskId, String texto) throws Exception {
        return mockMvc.perform(post("/tasks/{id}/comments", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CommentRequest(texto))));
    }

    @Test
    void comments_ofTheSameTask_getConsecutiveLines() throws Exception {
        long taskId = createTask();

        addComment(taskId, "Primero")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.linea").value(1))
                .andExpect(jsonPath("$.fechaAlta").isNotEmpty());
        addComment(taskId, "Segundo")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linea").value(2));

        mockMvc.perform(get("/tasks/{id}/comments", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].texto").value("Primero"))
                .andExpect(jsonPath("$[1].texto").value("Segundo"));
    }

    @Test
    void comments_ofTaskWithoutComments_isEmptyList() throws Exception {
        long taskId = createTask();

        mockMvc.perform(get("/tasks/{id}/comments", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void comment_onMissingTask_returnsNotFound() throws Exception {
        addComment(999_999, "Nadie lo leerá")
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tasks/{id}/comments", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void comment_withBlankTexto_returnsBadRequest() throws Exception {
        long taskId = createTask();

        addComment(taskId, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.texto").exists());
    }
}
