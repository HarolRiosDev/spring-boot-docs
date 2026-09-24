package dev.springbootdocs.examples.tasks;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@Configuration
public class KafkaConfig {

    private static final String TRUSTED_PACKAGE = "dev.springbootdocs.examples.tasks";
    private static final String CONSUMER_GROUP = "notifications";

    @Bean
    public ProducerFactory<String, TaskEvent> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        // TaskEventPublisher.onTaskEvent (AFTER_COMMIT) llama a send() de forma síncrona, en
        // el mismo hilo de la petición HTTP. Sin este límite, send() puede bloquear ese hilo
        // hasta el default de Kafka (60s) esperando metadata si el broker no responde —
        // acotado aquí para que una caída de Kafka nunca cuelgue una respuesta HTTP más de
        // unos segundos.
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, TaskEvent> kafkaTemplate(ProducerFactory<String, TaskEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, TaskEvent> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JacksonJsonDeserializer<TaskEvent> deserializer =
                new JacksonJsonDeserializer<>(TaskEvent.class).trustedPackages(TRUSTED_PACKAGE);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * Bean explícito en vez de confiar en la auto-configuración de Spring Boot: el
     * {@code ConsumerFactory<String, TaskEvent>} de arriba no calza por generics con el
     * {@code ObjectProvider<ConsumerFactory<Object, Object>>} que la auto-configuración usa
     * para construir su propio {@code kafkaListenerContainerFactory} — así que, sin este bean,
     * Spring Boot termina construyendo un consumer factory genérico propio, sin el
     * {@code group.id} que configuramos arriba, y {@code @KafkaListener} de {@code TaskEventListener} falla al
     * arrancar con "No group.id found". Al definir aquí el bean con el nombre exacto que
     * {@code @KafkaListener} busca por defecto, la auto-configuración de Boot retrocede
     * (@ConditionalOnMissingBean) y se usa este, construido sobre nuestro consumerFactory.
     *
     * <p>Nota: al definir este bean a mano en vez de dejar que Boot lo construya vía
     * {@code ConcurrentKafkaListenerContainerFactoryConfigurer}, las propiedades
     * {@code spring.kafka.listener.*}/{@code spring.kafka.template.*} de {@code application.yml}
     * dejan de aplicarse a este factory — cualquier ajuste futuro de auto-startup, concurrencia,
     * ack-mode, observation, etc. debe hacerse aquí, no en YAML.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TaskEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, TaskEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, TaskEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
