package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.execution.TaskQueueClaimService;
import io.github.inni.aetherflow.execution.TimeoutRecoveryScheduler;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.time.OffsetDateTime;
import java.util.List;
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
@Import({TestcontainersConfiguration.class, TimeoutRecoveryIntegrationTest.WorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class TimeoutRecoveryIntegrationTest {

	@Autowired
	private WorkflowEngine workflowEngine;

	@Autowired
	private TaskQueueClaimService taskQueueClaimService;

	@Autowired
	private TaskQueueRepository taskQueueRepository;

	@Autowired
	private StepRunRepository stepRunRepository;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private TimeoutRecoveryScheduler timeoutRecoveryScheduler;

	@Test
	void orphanedTaskIsRetriedWhenLockExpired() throws Exception {
		UUID runId = workflowEngine.start("recovery-test-workflow");

		// Claim the task so it moves to 'running'
		TaskQueueEntity task = taskQueueClaimService.claimNextTask("test-worker", 30).orElseThrow();

		// Manually expire the lock
		task.setLockExpiresAt(OffsetDateTime.now().minusSeconds(60));
		taskQueueRepository.save(task);

		// Trigger recovery
		timeoutRecoveryScheduler.recoverExpiredTasks();

		// Assert: a new pending task_queue row exists for the same (workflow_run_id, step_name)
		List<TaskQueueEntity> tasks = taskQueueRepository.findByWorkflowRunId(runId);
		long pendingCount = tasks.stream()
			.filter(t -> "pending".equals(t.getStatus()))
			.filter(t -> "recoverable-step".equals(t.getStepName()))
			.count();
		assertEquals(1, pendingCount, "Expected a new pending retry task to be enqueued");

		// Assert: the original step_run has status = 'retrying'
		var stepRuns = stepRunRepository.findByWorkflowRunId(runId);
		boolean hasRetrying = stepRuns.stream()
			.anyMatch(sr -> "retrying".equals(sr.getStatus()) && "recoverable-step".equals(sr.getStepName()));
		assertTrue(hasRetrying, "Expected the original step run to be in 'retrying' status");
	}

	@Test
	void orphanedTaskIsFailedWhenNoRetriesRemain() throws Exception {
		UUID runId = workflowEngine.start("no-retry-recovery-workflow");

		// Claim the task so it moves to 'running'
		TaskQueueEntity task = taskQueueClaimService.claimNextTask("test-worker", 30).orElseThrow();

		// Manually expire the lock
		task.setLockExpiresAt(OffsetDateTime.now().minusSeconds(60));
		taskQueueRepository.save(task);

		// Trigger recovery
		timeoutRecoveryScheduler.recoverExpiredTasks();

		// Assert: step_run is failed
		var stepRuns = stepRunRepository.findByWorkflowRunId(runId);
		assertEquals(1, stepRuns.size());
		assertEquals("failed", stepRuns.get(0).getStatus());

		// Assert: workflow_run is failed
		var workflowRun = workflowRunRepository.findById(runId).orElseThrow();
		assertEquals("failed", workflowRun.getStatus());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class WorkflowConfig {

		@Bean
		RecoveryTestWorkflow recoveryTestWorkflow() {
			return new RecoveryTestWorkflow();
		}

		@Bean
		NoRetryRecoveryWorkflow noRetryRecoveryWorkflow() {
			return new NoRetryRecoveryWorkflow();
		}
	}

	@AIWorkflow("recovery-test-workflow")
	static class RecoveryTestWorkflow {

		@Step(name = "recoverable-step", retries = 1)
		void recoverableStep() {
			// no-op — never actually executed in these tests
		}
	}

	@AIWorkflow("no-retry-recovery-workflow")
	static class NoRetryRecoveryWorkflow {

		@Step(name = "no-retry-step", retries = 0)
		void noRetryStep() {
			// no-op — never actually executed in these tests
		}
	}
}
