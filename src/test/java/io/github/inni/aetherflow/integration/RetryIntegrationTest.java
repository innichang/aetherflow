package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.engine.ExecutionStatus;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.execution.TaskPoller;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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
@Import({TestcontainersConfiguration.class, RetryIntegrationTest.RetryWorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class RetryIntegrationTest {

	@Autowired
	private WorkflowEngine workflowEngine;

	@Autowired
	private TaskPoller taskPoller;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private StepRunRepository stepRunRepository;

	@BeforeEach
	void resetCounters() {
		FlakeyStep.callCount.set(0);
		AlwaysFailStep.callCount.set(0);
	}

	@Test
	void retrySucceedsOnThirdAttemptAndWorkflowCompletes() throws Exception {
		// FlakeyStep fails on attempts 1 and 2, succeeds on attempt 3
		UUID runId = workflowEngine.start("flakey-workflow");

		for (int i = 0; i < 200; i++) {
			taskPoller.poll();
			var run = workflowRunRepository.findById(runId).orElseThrow();
			if (ExecutionStatus.COMPLETED.equals(run.getStatus()) || ExecutionStatus.FAILED.equals(run.getStatus())) {
				break;
			}
			Thread.sleep(10);
		}

		var run = workflowRunRepository.findById(runId).orElseThrow();
		assertEquals(ExecutionStatus.COMPLETED, run.getStatus());

		var stepRuns = stepRunRepository.findByWorkflowRunId(runId);
		assertEquals(3, stepRuns.size(), "Expected 3 step_run rows (3 attempts)");

		// attempts are 1, 2, 3
		var attempts = stepRuns.stream().map(sr -> sr.getAttempt()).sorted().toList();
		assertEquals(java.util.List.of(1, 2, 3), attempts);

		// first two are RETRYING, last is COMPLETED
		long retryingCount = stepRuns.stream().filter(sr -> ExecutionStatus.RETRYING.equals(sr.getStatus())).count();
		long completedCount = stepRuns.stream().filter(sr -> ExecutionStatus.COMPLETED.equals(sr.getStatus())).count();
		assertEquals(2, retryingCount);
		assertEquals(1, completedCount);

		// retry_count carries forward
		var attempt3 = stepRuns.stream().filter(sr -> sr.getAttempt() == 3).findFirst().orElseThrow();
		assertEquals(2, attempt3.getRetryCount());
	}

	@Test
	void exhaustsRetriesAndWorkflowFails() throws Exception {
		// AlwaysFailStep always fails; retries = 2 means 3 total attempts then FAILED
		UUID runId = workflowEngine.start("always-fail-workflow");

		for (int i = 0; i < 200; i++) {
			taskPoller.poll();
			var run = workflowRunRepository.findById(runId).orElseThrow();
			if (ExecutionStatus.FAILED.equals(run.getStatus())) {
				break;
			}
			Thread.sleep(10);
		}

		var run = workflowRunRepository.findById(runId).orElseThrow();
		assertEquals(ExecutionStatus.FAILED, run.getStatus());
		assertTrue(run.getErrorMessage().contains("always fails"));

		var stepRuns = stepRunRepository.findByWorkflowRunId(runId);
		// 3 total attempts (initial + 2 retries), last one is FAILED
		assertEquals(3, stepRuns.size(), "Expected 3 step_run rows (initial + 2 retries)");
		long failedCount = stepRuns.stream().filter(sr -> ExecutionStatus.FAILED.equals(sr.getStatus())).count();
		assertEquals(1, failedCount, "Only the terminal attempt should be FAILED");
		long retryingCount = stepRuns.stream().filter(sr -> ExecutionStatus.RETRYING.equals(sr.getStatus())).count();
		assertEquals(2, retryingCount, "First two attempts should be RETRYING");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RetryWorkflowConfig {

		@Bean
		FlakeyWorkflow flakeyWorkflow() {
			return new FlakeyWorkflow();
		}

		@Bean
		AlwaysFailWorkflow alwaysFailWorkflow() {
			return new AlwaysFailWorkflow();
		}
	}

	@AIWorkflow("flakey-workflow")
	static class FlakeyWorkflow {

		@Step(name = "flakey-step", retries = 2)
		void flakeyStep() {
			int call = FlakeyStep.callCount.incrementAndGet();
			if (call < 3) {
				throw new IllegalStateException("transient failure on attempt " + call);
			}
		}
	}

	@AIWorkflow("always-fail-workflow")
	static class AlwaysFailWorkflow {

		@Step(name = "always-fail-step", retries = 2)
		void alwaysFailStep() {
			AlwaysFailStep.callCount.incrementAndGet();
			throw new IllegalStateException("always fails");
		}
	}

	static class FlakeyStep {

		static final AtomicInteger callCount = new AtomicInteger(0);
	}

	static class AlwaysFailStep {

		static final AtomicInteger callCount = new AtomicInteger(0);
	}
}
