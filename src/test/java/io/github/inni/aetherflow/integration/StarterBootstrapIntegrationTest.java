package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.api.WorkflowRunController;
import io.github.inni.aetherflow.api.WorkflowRunQueryService;
import io.github.inni.aetherflow.config.AetherflowAutoConfiguration;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.execution.TaskPoller;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class StarterBootstrapIntegrationTest {

	@Nested
	@SpringBootTest
	@ActiveProfiles("test")
	@Import(TestcontainersConfiguration.class)
	class DefaultConfiguration {

		@Autowired
		private ApplicationContext context;

		@Test
		void coreEngineBeans_arePresent() {
			assertNotNull(context.getBean(WorkflowEngine.class));
			assertNotNull(context.getBean(TaskPoller.class));
		}

		@Test
		void autoConfigurationClass_isLoaded() {
			assertNotNull(context.getBean(AetherflowAutoConfiguration.class));
		}

		@Test
		void apiBeans_arePresentByDefault() {
			assertNotNull(context.getBean(WorkflowRunController.class));
			assertNotNull(context.getBean(WorkflowRunQueryService.class));
		}
	}

	@Nested
	@SpringBootTest(properties = "aetherflow.api.enabled=false")
	@ActiveProfiles("test")
	@Import(TestcontainersConfiguration.class)
	class ApiDisabled {

		@Autowired
		private ApplicationContext context;

		@Test
		void coreEngineBeans_stillPresent() {
			assertNotNull(context.getBean(WorkflowEngine.class));
			assertNotNull(context.getBean(TaskPoller.class));
		}

		@Test
		void apiBeans_areAbsent() {
			assertFalse(context.containsBean("workflowRunController"),
				"WorkflowRunController should not be registered when api.enabled=false");
			assertFalse(context.containsBean("workflowRunQueryService"),
				"WorkflowRunQueryService should not be registered when api.enabled=false");
		}
	}
}
