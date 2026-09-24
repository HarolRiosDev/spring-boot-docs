package dev.springbootdocs.examples.tasks.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escucha el TaskEvent que TaskServiceImpl publica como evento de aplicación de Spring
 * DENTRO de su propia transacción, y solo lo reenvía a Kafka una vez que esa transacción
 * ya confirmó (AFTER_COMMIT). Si la transacción hace rollback, este método nunca se ejecuta
 * — así un evento nunca llega a Kafka anunciando algo que en realidad no ocurrió.
 */
@Component
public class TaskEventPublisher {

    private static final String TOPIC = "task-events";

    private final KafkaTemplate<String, TaskEvent> kafkaTemplate;

    public TaskEventPublisher(KafkaTemplate<String, TaskEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskEvent(TaskEvent event) {
        kafkaTemplate.send(TOPIC, event.taskId().toString(), event);
    }
}
