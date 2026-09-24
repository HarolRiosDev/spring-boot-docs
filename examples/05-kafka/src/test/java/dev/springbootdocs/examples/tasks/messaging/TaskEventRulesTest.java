package dev.springbootdocs.examples.tasks.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.model.Notification;
import dev.springbootdocs.examples.tasks.model.User;
import dev.springbootdocs.examples.tasks.repository.NotificationRepository;
import dev.springbootdocs.examples.tasks.repository.UserRepository;
import dev.springbootdocs.examples.tasks.service.TaskServiceImpl;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "task-events")
class TaskEventRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TaskServiceImpl taskService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    private Long createTask(String token, boolean completada) throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Tarea", "desc", completada))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void updatingAlreadyCompletedTask_doesNotPublishAnotherCompletedEvent() throws Exception {
        String username = "beatriz";
        String token = registerAndLogin(username);
        Long taskId = createTask(token, true); // ya nace completada -> solo CREATED, nunca COMPLETED

        // Actualizarla otra vez, siempre con completada=true: no hay transicion false->true
        mockMvc.perform(put("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Tarea editada", "desc", true))))
                .andExpect(status().isOk());

        // Barrera de sincronizacion: crear una SEGUNDA tarea real y esperar su notificacion CREATED.
        // Como los eventos de un mismo consumer se procesan en orden, cuando esta llega ya se proceso
        // (o no) cualquier evento anterior - evita un Thread.sleep arbitrario para probar una ausencia.
        Long secondTaskId = createTask(token, false);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            User user = userRepository.findByUsername(username).orElseThrow();
            boolean secondTaskNotified = notificationRepository.findByUser(user).stream()
                    .anyMatch(n -> n.getTaskId().equals(secondTaskId));
            assertThat(secondTaskNotified).isTrue();
        });

        User user = userRepository.findByUsername(username).orElseThrow();
        long completedCount = notificationRepository.findByUser(user).stream()
                .filter(n -> n.getTaskId().equals(taskId) && n.getEventType() == TaskEventType.COMPLETED)
                .count();
        assertThat(completedCount).isZero();
    }

    @Test
    void completingTask_firstTransitionFalseToTrue_publishesCompletedEvent() throws Exception {
        String username = "carlos";
        String token = registerAndLogin(username);
        Long taskId = createTask(token, false);

        mockMvc.perform(put("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Tarea", "desc", true))))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            User user = userRepository.findByUsername(username).orElseThrow();
            boolean hasCompletedEvent = notificationRepository.findByUser(user).stream()
                    .anyMatch(n -> n.getTaskId().equals(taskId) && n.getEventType() == TaskEventType.COMPLETED);
            assertThat(hasCompletedEvent).isTrue();
        });
    }

    @Test
    void whenTransactionRollsBack_noEventReachesKafka() throws Exception {
        String username = "diana";
        registerAndLogin(username);
        User user = userRepository.findByUsername(username).orElseThrow();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            taskService.create(new TaskRequest("Tarea que no debe persistir", "desc", false), user);
            status.setRollbackOnly();
            return null;
        });

        // Barrera de sincronizacion: la misma tecnica que en el test de arriba, con una tarea real
        // hecha por otro usuario para no interferir con las notificaciones de "diana".
        String otherToken = registerAndLogin("erik");
        Long barrierTaskId = createTask(otherToken, false);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            User erik = userRepository.findByUsername("erik").orElseThrow();
            boolean barrierNotified = notificationRepository.findByUser(erik).stream()
                    .anyMatch(n -> n.getTaskId().equals(barrierTaskId));
            assertThat(barrierNotified).isTrue();
        });

        List<Notification> dianaNotifications = notificationRepository.findByUser(user);
        assertThat(dianaNotifications).isEmpty();
    }

    @Test
    void adminCompletingAnotherUsersTask_notifiesTheOwner_notTheAdmin() throws Exception {
        String ownerUsername = "felipe";
        String ownerToken = registerAndLogin(ownerUsername);
        Long taskId = createTask(ownerToken, false);

        MvcResult adminLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin12345"))))
                .andReturn();
        String adminToken = objectMapper.readTree(adminLoginResult.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(put("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Tarea", "desc", true))))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            User owner = userRepository.findByUsername(ownerUsername).orElseThrow();
            boolean ownerNotified = notificationRepository.findByUser(owner).stream()
                    .anyMatch(n -> n.getTaskId().equals(taskId) && n.getEventType() == TaskEventType.COMPLETED);
            assertThat(ownerNotified).isTrue();
        });

        User admin = userRepository.findByUsername("admin").orElseThrow();
        boolean adminWronglyNotified = notificationRepository.findByUser(admin).stream()
                .anyMatch(n -> n.getTaskId().equals(taskId));
        assertThat(adminWronglyNotified).isFalse();
    }
}
