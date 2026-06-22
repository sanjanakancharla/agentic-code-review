package com.sanjana.agentic_code_review;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AgenticCodeReviewApplicationTests {

	@Test
	void contextLoads() {
	}

}
