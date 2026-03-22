package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.execution.TaskQueueClaimService;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.Optional;
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
@Import({TestcontainersConfiguration.class, ConcurrencyLimitIntegrationTest.ConcurrencyWorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class ConcurrencyLimitIntegrationTest {

	@Autowired
	private WorkflowEngine workflowEngine;

	@Autowired
	private TaskQueueClaimService taskQueueClaimService;

	@Autowired
	private TaskQueueRepository taskQueueRepository;

	@Test
	void concurrencyLimitIsEnforcedAndReleasedOnCompletion() throws Exception {
		// Start two workflow runs — each enqueues one pending task for "limited-step"
		workflowEngine.start("concurrency-test-workflow");
		workflowEngine.start("concurrency-test-workflow");

		// First claim: should succeed since no tasks are running yet
		Optional<TaskQueueEntity> task1 = taskQueueClaimService.claimNextTask("worker-A", 30);
		assertTrue(task1.isPresent(), "First claim should succeed when no tasks are running");

		// Second claim: should be blocked because concurrency = 1 and one task is running
		Optional<TaskQueueEntity> task2 = taskQueueClaimService.claimNextTask("worker-B", 30);
		assertFalse(task2.isPresent(), "Second claim should be blocked by concurrency limit of 1");

		// Verify DB state: 1 running, 1 pending
		long runningCount = taskQueueRepository.findAll().stream()
			.filter(t -> "running".equals(t.getStatus()))
			.count();
		long pendingCount = taskQueueRepository.findAll().stream()
			.filter(t -> "pending".equals(t.getStatus()))
			.count();
		assertEquals(1, runningCount, "Exactly 1 task should be in running state");
		assertEquals(1, pendingCount, "Exactly 1 task should still be in pending state");

		// Mark task1 as completed to release the concurrency slot
		TaskQueueEntity completedTask = task1.get();
		completedTask.setStatus("completed");
		taskQueueRepository.save(completedTask);

		// Third claim: should now succeed because no tasks are running
		Optional<TaskQueueEntity> task3 = taskQueueClaimService.claimNextTask("worker-C", 30);
		assertTrue(task3.isPresent(), "Claim should succeed after previous task is completed");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ConcurrencyWorkflowConfig {

		@Bean
		ConcurrencyTestWorkflow concurrencyTestWorkflow() {
			return new ConcurrencyTestWorkflow();
		}
	}

	@AIWorkflow("concurrency-test-workflow")
	static class ConcurrencyTestWorkflow {

		@Step(name = "limited-step", concurrency = 1)
		void limitedStep() {
			// No-op: test controls task state directly via repository
		}
	}
}
