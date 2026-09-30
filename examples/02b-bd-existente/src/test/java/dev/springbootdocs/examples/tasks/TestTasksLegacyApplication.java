package dev.springbootdocs.examples.tasks;

import org.springframework.boot.SpringApplication;

public class TestTasksLegacyApplication {

	public static void main(String[] args) {
		SpringApplication.from(TasksLegacyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
