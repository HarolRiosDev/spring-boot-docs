package dev.springbootdocs.examples.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Si el contexto arranca, Flyway ha aplicado sus migraciones y Hibernate ha validado
// (ddl-auto: validate) todas las entidades contra el esquema heredado real
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TasksLegacyApplicationIT {

	@Test
	void contextLoads() {
	}

}
