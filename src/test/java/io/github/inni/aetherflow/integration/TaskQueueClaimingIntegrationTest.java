package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.engine.ExecutionStatus;
import io.github.inni.aetherflow.execution.TaskQueueClaimService;
import io.github.inni.aetherflow.persistence.entity.StepEntity;
import io.github.inni.aetherflow.persistence.entity.StepRunEntity;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.entity.WorkflowEntity;
import io.github.inni.aetherflow.persistence.entity.WorkflowRunEntity;
import io.github.inni.aetherflow.persistence.repository.StepRepository;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class TaskQueueClaimingIntegrationTest {

	@Autowired
	private TaskQueueClaimService taskQueueClaimService;

	@Autowired
	private WorkflowRepository workflowRepository;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private StepRepository stepRepository;

	@Autowired
	private StepRunRepository stepRunRepository;

	@Autowired
	private TaskQueueRepository taskQueueRepository;

	@Test
	void concurrentWorkersClaimDifferentTasks() throws Exception {
		WorkflowEntity workflow = createWorkflow();
		StepEntity step = createStep(workflow.getId(), "onlyStep");
		createPendingTask(workflow, step, UUID.randomUUID());
		createPendingTask(workflow, step, UUID.randomUUID());

		Set<UUID> claimedTaskIds = ConcurrentHashMap.newKeySet();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			executor.submit(() -> claimTask("worker-a", ready, start, claimedTaskIds));
			executor.submit(() -> claimTask("worker-b", ready, start, claimedTaskIds));
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
		} finally {
			executor.shutdown();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
		}

		assertEquals(2, claimedTaskIds.size());
	}

	private void claimTask(String workerId, CountDownLatch ready, CountDownLatch start, Set<UUID> claimedTaskIds) {
		try {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			taskQueueClaimService.claimNextTask(workerId, 30).ifPresent(task -> claimedTaskIds.add(task.getId()));
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private WorkflowEntity createWorkflow() {
		WorkflowEntity workflow = new WorkflowEntity();
		workflow.setId(UUID.randomUUID());
		workflow.setWorkflowName("claim-test-" + workflow.getId());
		workflow.setWorkflowVersion(1);
		workflow.setBeanName("bean");
		workflow.setClassName("class");
		return workflowRepository.save(workflow);
	}

	private StepEntity createStep(UUID workflowId, String stepName) {
		StepEntity step = new StepEntity();
		step.setId(UUID.randomUUID());
		step.setWorkflowId(workflowId);
		step.setStepName(stepName);
		step.setMethodName(stepName);
		step.setDependsOn("");
		step.setRetries(0);
		return stepRepository.save(step);
	}

	private void createPendingTask(WorkflowEntity workflow, StepEntity step, UUID runId) {
		WorkflowRunEntity workflowRun = new WorkflowRunEntity();
		workflowRun.setId(runId);
		workflowRun.setWorkflowId(workflow.getId());
		workflowRun.setWorkflowName(workflow.getWorkflowName());
		workflowRun.setWorkflowVersion(workflow.getWorkflowVersion());
		workflowRun.setStatus(ExecutionStatus.RUNNING);
		workflowRun.setStartedAt(OffsetDateTime.now());
		workflowRunRepository.save(workflowRun);

		StepRunEntity stepRun = new StepRunEntity();
		stepRun.setId(UUID.randomUUID());
		stepRun.setWorkflowRunId(workflowRun.getId());
		stepRun.setWorkflowId(workflow.getId());
		stepRun.setStepId(step.getId());
		stepRun.setStepName(step.getStepName());
		stepRun.setStatus(ExecutionStatus.PENDING);
		stepRun.setAttempt(1);
		stepRun.setMaxRetries(0);
		stepRun.setRetryCount(0);
		stepRunRepository.save(stepRun);

		TaskQueueEntity taskQueue = new TaskQueueEntity();
		taskQueue.setId(UUID.randomUUID());
		taskQueue.setWorkflowRunId(workflowRun.getId());
		taskQueue.setStepRunId(stepRun.getId());
		taskQueue.setWorkflowName(workflow.getWorkflowName());
		taskQueue.setStepName(step.getStepName());
		taskQueue.setStatus(ExecutionStatus.PENDING);
		taskQueue.setPriority(100);
		taskQueue.setAvailableAt(OffsetDateTime.now());
		taskQueueRepository.save(taskQueue);
	}
}

