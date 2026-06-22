package com.sanjana.agentic_code_review;

import org.springframework.boot.SpringApplication;

public class TestAgenticCodeReviewApplication {

	public static void main(String[] args) {
		SpringApplication.from(AgenticCodeReviewApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
