package dev.springbootdocs.examples.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Lee el contador directamente del MeterRegistry de la aplicación. No necesita
 * {@code @AutoConfigureMetrics}: los contadores se registran igual en los tests; lo que
 * Spring Boot desactiva es exportarlos (por ejemplo, el endpoint /actuator/prometheus).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    private String registerAndLogin(String username) throws Exception {
        String password = "password123";
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private double taskCreations() {
        return meterRegistry.get("tasks.creations").counter().count();
    }

    @Test
    void createTask_incrementsTasksCreationsCounter() throws Exception {
        String token = registerAndLogin("metrics-ana");
        double before = taskCreations();

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Medir", "desc", false))))
                .andExpect(status().isCreated());

        assertThat(taskCreations()).isEqualTo(before + 1);
    }

    @Test
    void rejectedTask_doesNotIncrementCounter() throws Exception {
        String token = registerAndLogin("metrics-beto");
        double before = taskCreations();

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("", "desc", false))))
                .andExpect(status().isBadRequest());

        assertThat(taskCreations()).isEqualTo(before);
    }
}
