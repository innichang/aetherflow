package io.github.inni.aetherflow.engine.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import io.github.inni.aetherflow.workflow.registry.WorkflowScanner;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class WorkflowScannerTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfig.class);

	@Test
	void scansAnnotatedWorkflowBean() {
		contextRunner.run(context -> {
			WorkflowScanner scanner = new WorkflowScanner(context);
			List<WorkflowScanner.ScannedWorkflow> workflows = scanner.scan();
			assertEquals(1, workflows.size());

			WorkflowScanner.ScannedWorkflow scanned = workflows.get(0);
			assertEquals("scanner-test-workflow", scanned.definition().workflowName());
			assertEquals(2, scanned.definition().steps().size());

			var stepNames = scanned.definition().steps().stream().map(step -> step.stepName()).toList();
			assertTrue(stepNames.contains("first"));
			assertTrue(stepNames.contains("second"));
			assertNotNull(scanned.invokersByStepName().get("first"));
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		ScannerWorkflow scannerWorkflow() {
			return new ScannerWorkflow();
		}
	}

	@AIWorkflow("scanner-test-workflow")
	static class ScannerWorkflow {

		@Step(name = "first")
		void stepOne() {
		}

		@Step(name = "second", dependsOn = {"first"})
		void stepTwo() {
		}
	}
}

