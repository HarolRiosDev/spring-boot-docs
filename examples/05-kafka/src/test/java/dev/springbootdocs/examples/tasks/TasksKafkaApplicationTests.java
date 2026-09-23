package dev.springbootdocs.examples.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "task-events")
class TasksKafkaApplicationTests {

	@Test
	void contextLoads() {
	}

}
