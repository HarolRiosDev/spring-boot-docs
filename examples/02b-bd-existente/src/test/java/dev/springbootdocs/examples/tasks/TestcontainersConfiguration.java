package dev.springbootdocs.examples.tasks;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// El mismo script del DBA que usa docker-compose.yml: Postgres lo ejecuta al crear el contenedor
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
				.withCopyFileToContainer(
						MountableFile.forHostPath("docker/legacy-schema.sql", 0644),
						"/docker-entrypoint-initdb.d/legacy-schema.sql");
	}

}
