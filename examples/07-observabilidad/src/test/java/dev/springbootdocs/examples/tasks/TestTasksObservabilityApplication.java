package dev.springbootdocs.examples.tasks;

import org.springframework.boot.SpringApplication;

public class TestTasksObservabilityApplication {

	public static void main(String[] args) {
		SpringApplication.from(TasksObservabilityApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
