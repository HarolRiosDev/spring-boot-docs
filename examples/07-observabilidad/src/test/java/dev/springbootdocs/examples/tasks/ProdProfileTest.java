package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.service.TaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * La misma aplicación con el perfil "prod" (src/main/resources/application-prod.yml):
 * logs en JSON, Swagger UI apagado y health sin detalles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@ExtendWith(OutputCaptureExtension.class)
class ProdProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, "password123"))));
        return login(username, "password123");
    }

    @Test
    void logs_areJsonLines_withTraceId(CapturedOutput output) throws Exception {
        String token = registerAndLogin("prod-elena");

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("En JSON", "desc", false))))
                .andExpect(status().isCreated());

        String line = output.getOut().lines()
                .filter(l -> l.contains("creada por prod-elena"))
                .findFirst()
                .orElseThrow();
        JsonNode json = objectMapper.readTree(line);
        assertThat(json.get("message").asText()).endsWith("creada por prod-elena");
        assertThat(json.at("/log/logger").asText()).isEqualTo(TaskServiceImpl.class.getName());
        assertThat(json.get("traceId").asText()).matches("[0-9a-f]{32}");
    }

    @Test
    void health_neverShowsDetails_evenForAdmin() throws Exception {
        String adminToken = login("admin", "admin12345");

        MvcResult result = mockMvc.perform(get("/actuator/health").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).has("components")).isFalse();
    }
}
