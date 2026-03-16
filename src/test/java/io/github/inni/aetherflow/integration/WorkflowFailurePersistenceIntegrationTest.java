package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.execution.TaskPoller;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, WorkflowFailurePersistenceIntegrationTest.FailingWorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class WorkflowFailurePersistenceIntegrationTest {

	@Autowired
	private WorkflowEngine workflowEngine;

	@Autowired
	private TaskPoller taskPoller;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private StepRunRepository stepRunRepository;

	@Test
	void persistsFailureOnStepException() throws Exception {
		UUID runId = workflowEngine.start("failing-workflow");

		for (int i = 0; i < 100; i++) {
			taskPoller.poll();
			var run = workflowRunRepository.findById(runId).orElseThrow();
			if ("failed".equals(run.getStatus())) {
				break;
			}
			Thread.sleep(20);
		}

		var run = workflowRunRepository.findById(runId).orElseThrow();
		assertEquals("failed", run.getStatus());
		assertTrue(run.getErrorMessage().contains("boom"));

		var stepRuns = stepRunRepository.findByWorkflowRunId(runId);
		assertEquals(1, stepRuns.size());
		assertEquals("failed", stepRuns.get(0).getStatus());
		assertTrue(stepRuns.get(0).getErrorMessage().contains("boom"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FailingWorkflowConfig {

		@Bean
		FailingWorkflow failingWorkflow() {
			return new FailingWorkflow();
		}
	}

	@AIWorkflow("failing-workflow")
	static class FailingWorkflow {

		@Step(name = "explode")
		void explode() {
			throw new IllegalStateException("boom");
		}
	}
}

