package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.engine.ExecutionStatus;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.execution.ResultReporter;
import io.github.inni.aetherflow.execution.TaskPoller;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
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
@Import({TestcontainersConfiguration.class, IdempotencyIntegrationTest.IdempotencyWorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class IdempotencyIntegrationTest {

	@Autowired
	private WorkflowEngine workflowEngine;

	@Autowired
	private TaskPoller taskPoller;

	@Autowired
	private ResultReporter resultReporter;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private StepRunRepository stepRunRepository;

	@Autowired
	private TaskQueueRepository taskQueueRepository;

	@Test
	void allStepRunsHaveIdempotencyKeysAfterCompletion() throws Exception {
		UUID runId = workflowEngine.start("idempotency-workflow");

		for (int i = 0; i < 100; i++) {
			taskPoller.poll();
			var run = workflowRunRepository.findById(runId).orElseThrow();
			if (ExecutionStatus.COMPLETED.equals(run.getStatus())) {
				break;
			}
			Thread.sleep(20);
		}

		assertEquals(ExecutionStatus.COMPLETED, workflowRunRepository.findById(runId).orElseThrow().getStatus());

		var stepRuns = stepRunRepository.findByWorkflowRunId(runId);
		assertEquals(2, stepRuns.size());
		stepRuns.forEach(sr -> assertNotNull(sr.getIdempotencyKey(), "idempotencyKey must be set on " + sr.getStepName()));
		stepRuns.forEach(sr -> assertTrue(
			sr.getIdempotencyKey().contains(sr.getStepName()),
			"idempotencyKey should contain step name"
		));
	}

	@Test
	void doubleReportDoesNotCreateDuplicateStepRuns() throws Exception {
		// Run a two-step workflow: step-one -> step-two
		// After step-one completes, call report() a second time with the same completed task
		// and verify that step-two still has exactly one step_run row.
		UUID runId = workflowEngine.start("idempotency-workflow");

		// Poll until step-one is done (step-two should be enqueued once)
		TaskQueueEntity stepOneTask = null;
		for (int i = 0; i < 100; i++) {
			taskPoller.poll();
			var tasks = taskQueueRepository.findByWorkflowRunId(runId).stream()
				.filter(t -> "step-one".equals(t.getStepName()))
				.filter(t -> ExecutionStatus.COMPLETED.equals(t.getStatus()))
				.findFirst();
			if (tasks.isPresent()) {
				stepOneTask = tasks.get();
				break;
			}
			Thread.sleep(20);
		}

		assertNotNull(stepOneTask, "step-one task should have completed");

		// At this point step-two has been enqueued once; record count
		long stepTwoRunsBefore = stepRunRepository.findByWorkflowRunId(runId).stream()
			.filter(sr -> "step-two".equals(sr.getStepName()))
			.count();
		assertEquals(1, stepTwoRunsBefore, "step-two should have exactly one step_run after step-one completes");

		// Simulate duplicate report of the same completed step-one task — should be a no-op
		final TaskQueueEntity finalStepOneTask = stepOneTask;
		try {
			resultReporter.report(finalStepOneTask, io.github.inni.aetherflow.execution.ExecutionResult.success(0));
		} catch (Exception ignored) {
			// report() on an already-completed task may throw due to state; that's acceptable
		}

		// step-two must still have exactly one step_run
		long stepTwoRunsAfter = stepRunRepository.findByWorkflowRunId(runId).stream()
			.filter(sr -> "step-two".equals(sr.getStepName()))
			.count();
		assertEquals(1, stepTwoRunsAfter, "duplicate report must not create a second step_run for step-two");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class IdempotencyWorkflowConfig {

		@Bean
		IdempotencyWorkflow idempotencyWorkflow() {
			return new IdempotencyWorkflow();
		}
	}

	@AIWorkflow("idempotency-workflow")
	static class IdempotencyWorkflow {

		@Step(name = "step-one")
		void stepOne() {
		}

		@Step(name = "step-two", dependsOn = "step-one")
		void stepTwo() {
		}
	}
}
