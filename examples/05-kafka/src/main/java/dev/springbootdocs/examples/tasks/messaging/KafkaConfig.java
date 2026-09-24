package dev.springbootdocs.examples.tasks.messaging;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String TASK_EVENTS_TOPIC = "task-events";
    // Nombre que DeadLetterPublishingRecoverer usa por defecto: el topic original + "-dlt".
    // Ojo: en Spring Kafka 3.x el sufijo era ".DLT"; tutoriales anteriores usan ese nombre.
    public static final String TASK_EVENTS_DLT = TASK_EVENTS_TOPIC + "-dlt";

    private static final String TRUSTED_PACKAGE = "dev.springbootdocs.examples.tasks.messaging";
    private static final String CONSUMER_GROUP = "notifications";

    // Topics declarados como beans: KafkaAdmin (autoconfigurado por Spring Boot) los crea al
    // arrancar si no existen, sin depender de que el broker tenga auto.create.topics.enable.
    // La DLT necesita al menos tantas particiones como el topic original: el recoverer envía
    // cada mensaje fallido a la MISMA partición de la que venía.
    @Bean
    public NewTopic taskEventsTopic() {
        return TopicBuilder.name(TASK_EVENTS_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic taskEventsDeadLetterTopic() {
        return TopicBuilder.name(TASK_EVENTS_DLT).partitions(1).replicas(1).build();
    }

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
        JacksonJsonDeserializer<TaskEvent> jsonDeserializer =
                new JacksonJsonDeserializer<>(TaskEvent.class).trustedPackages(TRUSTED_PACKAGE);
        // Sin este envoltorio, un mensaje con JSON inválido (un "poison pill") hace fallar al
        // consumidor ANTES de llegar al listener, en cada poll, sin que ningún manejador de
        // errores pueda sacarlo de ahí. ErrorHandlingDeserializer captura ese fallo y entrega
        // el mensaje con valor null y la excepción en un header, para que DefaultErrorHandler
        // pueda mandarlo a la DLT como cualquier otro error.
        ErrorHandlingDeserializer<TaskEvent> deserializer = new ErrorHandlingDeserializer<>(jsonDeserializer);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * Template solo para la DLT. Un mensaje que falló al deserializarse no tiene un TaskEvent
     * que reenviar, sino los bytes originales tal como llegaron; este template los publica sin
     * transformarlos. Los mensajes que sí se deserializaron (TaskEvent) salen por el
     * kafkaTemplate normal, con su serializador JSON.
     */
    @Bean
    public KafkaTemplate<String, byte[]> deadLetterBytesTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /**
     * Qué hacer cuando procesar un mensaje falla: reintentarlo 2 veces más, con 1 segundo entre
     * intentos, y si sigue fallando publicarlo en task-events-dlt en vez de perderlo. Los errores
     * de deserialización no se reintentan (DefaultErrorHandler los considera no recuperables:
     * el mismo JSON inválido va a fallar igual) y van directos a la DLT. En ambos casos el
     * consumidor avanza al siguiente mensaje: uno defectuoso nunca bloquea a los demás.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, TaskEvent> kafkaTemplate,
            KafkaTemplate<String, byte[]> deadLetterBytesTemplate) {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, deadLetterBytesTemplate);
        templates.put(TaskEvent.class, kafkaTemplate);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
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
            ConsumerFactory<String, TaskEvent> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TaskEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
