package dev.springbootdocs.examples.tasks;

import org.springframework.boot.SpringApplication;

public class TestTasksTestingApplication {

	public static void main(String[] args) {
		SpringApplication.from(TasksTestingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
