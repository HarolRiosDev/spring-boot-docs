package dev.springbootdocs.examples.tasks.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.model.User;
import dev.springbootdocs.examples.tasks.repository.NotificationRepository;
import dev.springbootdocs.examples.tasks.repository.UserRepository;
import java.time.Duration;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "task-events")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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

    private void awaitNotificationFor(String username) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            User user = userRepository.findByUsername(username).orElseThrow();
            assertThat(notificationRepository.findByUser(user)).hasSize(1);
        });
    }

    @Test
    void user_seesOwnNotification() throws Exception {
        String username = "nora";
        String token = registerAndLogin(username);

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Regar plantas", "desc", false))))
                .andExpect(status().isCreated());

        awaitNotificationFor(username);

        MvcResult result = mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("mensaje").asText()).contains("Regar plantas");
    }

    @Test
    void admin_seesNotificationsFromOtherUsers() throws Exception {
        String username = "oscar";
        String token = registerAndLogin(username);

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Lavar el auto", "desc", false))))
                .andExpect(status().isCreated());

        awaitNotificationFor(username);

        String adminToken = loginAsAdmin();

        MvcResult result = mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        boolean containsOscarsNotification = StreamSupport.stream(body.spliterator(), false)
                .anyMatch(node -> node.get("mensaje").asText().contains("Lavar el auto"));
        assertThat(containsOscarsNotification).isTrue();
    }
}
