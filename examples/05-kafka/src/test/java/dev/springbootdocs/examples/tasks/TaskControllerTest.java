package dev.springbootdocs.examples.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "task-events")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin12345"))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Long createTask(String token, String titulo) throws Exception {
        String json = objectMapper.writeValueAsString(new TaskRequest(titulo, "desc", false));
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createTask_withoutToken_returnsUnauthorized() throws Exception {
        String json = objectMapper.writeValueAsString(new TaskRequest("Comprar leche", "2 litros", false));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTask_withToken_returnsCreated() throws Exception {
        String token = registerAndLogin("frank");
        String json = objectMapper.writeValueAsString(new TaskRequest("Comprar leche", "2 litros", false));

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Comprar leche"))
                .andExpect(jsonPath("$.username").value("frank"));
    }

    @Test
    void listTasks_onlyReturnsOwnTasks_forRegularUser() throws Exception {
        String graceToken = registerAndLogin("grace");
        String henryToken = registerAndLogin("henry");
        createTask(graceToken, "Tarea de grace");
        createTask(henryToken, "Tarea de henry");

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + graceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Tarea de grace"));
    }

    @Test
    void listTasks_returnsAllTasks_forAdmin() throws Exception {
        String ivyToken = registerAndLogin("ivy");
        createTask(ivyToken, "Tarea de ivy");
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titulo == 'Tarea de ivy')]").exists());
    }

    @Test
    void getTaskById_whenOwner_returnsTask() throws Exception {
        String token = registerAndLogin("jack");
        Long id = createTask(token, "Tarea de jack");

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getTaskById_whenNotOwnerAndNotAdmin_returnsForbidden() throws Exception {
        String ownerToken = registerAndLogin("kate");
        String otherToken = registerAndLogin("liam");
        Long id = createTask(ownerToken, "Tarea de kate");

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTaskById_whenNotOwnerButAdmin_returnsTask() throws Exception {
        String ownerToken = registerAndLogin("mia");
        Long id = createTask(ownerToken, "Tarea de mia");
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getTaskById_whenNotFound_returns404() throws Exception {
        String token = registerAndLogin("noah");

        mockMvc.perform(get("/tasks/{id}", 999999L).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTask_whenOwner_returnsUpdatedTask() throws Exception {
        String token = registerAndLogin("olivia");
        Long id = createTask(token, "Original");
        String updateJson = objectMapper.writeValueAsString(new TaskRequest("Actualizada", "nueva desc", true));

        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Actualizada"))
                .andExpect(jsonPath("$.completada").value(true));
    }

    @Test
    void updateTask_whenNotOwner_returnsForbidden() throws Exception {
        String ownerToken = registerAndLogin("peter");
        String otherToken = registerAndLogin("quinn");
        Long id = createTask(ownerToken, "Tarea de peter");
        String updateJson = objectMapper.writeValueAsString(new TaskRequest("Hackeada", null, false));

        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteTask_whenOwner_thenReturns404OnFollowUpGet() throws Exception {
        String token = registerAndLogin("rachel");
        Long id = createTask(token, "Tarea de rachel");

        mockMvc.perform(delete("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_whenNotOwner_returnsForbidden() throws Exception {
        String ownerToken = registerAndLogin("steve");
        String otherToken = registerAndLogin("tina");
        Long id = createTask(ownerToken, "Tarea de steve");

        mockMvc.perform(delete("/tasks/{id}", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTask_withTituloOverMaxLength_returnsBadRequest() throws Exception {
        String token = registerAndLogin("victor");
        String tituloTooLong = "a".repeat(256);
        String json = objectMapper.writeValueAsString(new TaskRequest(tituloTooLong, "desc", false));

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.titulo").exists());
    }

    @Test
    void accessProtectedEndpoint_withTamperedToken_returnsUnauthorized() throws Exception {
        String token = registerAndLogin("uma");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
