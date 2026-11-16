package com.farhaan.projects.Ace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")   // uses an in-memory H2 database (see src/test/resources/application-test.yaml)
class AceApplicationTests {

	@Test
	void contextLoads() {

	}

}
