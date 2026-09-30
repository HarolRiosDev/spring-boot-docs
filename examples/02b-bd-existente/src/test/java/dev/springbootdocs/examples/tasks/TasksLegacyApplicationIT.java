package dev.springbootdocs.examples.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

// Si el contexto arranca, Flyway ha aplicado sus migraciones y Hibernate ha validado
// (ddl-auto: validate) todas las entidades contra el esquema heredado real.
// @AutoConfigureMockMvc no se usa aquí, pero deja la configuración igual que la del resto
// de *IT: así todas comparten un único contexto y un único contenedor
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TasksLegacyApplicationIT {

	@Test
	void contextLoads() {
	}

}
