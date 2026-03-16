package io.github.inni.aetherflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class AetherflowApplicationTests {

	@Test
	void contextLoads() {
	}

}
