package dev.springbootdocs.examples.tasks.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.model.User;
import dev.springbootdocs.examples.tasks.repository.NotificationRepository;
import dev.springbootdocs.examples.tasks.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {KafkaConfig.TASK_EVENTS_TOPIC, KafkaConfig.TASK_EVENTS_DLT})
class DeadLetterTopicTest {

    private static final String POISON = "esto no es un TaskEvent en JSON";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void poisonMessage_endsUpInDeadLetterTopic_andConsumerKeepsProcessing() throws Exception {
        // 1. Un mensaje que no es JSON válido, escrito directamente en el topic (sin pasar por
        //    la aplicación, que nunca produciría algo así).
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(KafkaConfig.TASK_EVENTS_TOPIC, "roto", POISON)).get();
        }

        // 2. Acaba en la DLT con los bytes originales intactos y la causa del fallo en un header.
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafka, "dlt-test", false);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer =
                new KafkaConsumer<>(consumerProps, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaConfig.TASK_EVENTS_DLT));
            ConsumerRecord<String, String> deadLetter =
                    KafkaTestUtils.getSingleRecord(consumer, KafkaConfig.TASK_EVENTS_DLT, Duration.ofSeconds(15));

            assertThat(deadLetter.value()).isEqualTo(POISON);
            Header cause = deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN);
            assertThat(cause).isNotNull();
            assertThat(new String(cause.value(), StandardCharsets.UTF_8))
                    .isEqualTo("org.springframework.kafka.support.serializer.DeserializationException");
        }

        // 3. El mensaje defectuoso no bloqueó el topic: un evento normal, publicado después,
        //    sigue llegando al consumidor y genera su notificación.
        String username = "dora";
        String token = registerAndLogin(username);
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Después del mensaje roto", "desc", false))))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            User user = userRepository.findByUsername(username).orElseThrow();
            assertThat(notificationRepository.findByUser(user)).hasSize(1);
        });
    }

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
}
