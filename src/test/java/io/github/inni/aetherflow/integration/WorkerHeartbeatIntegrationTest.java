package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.execution.TaskPoller;
import io.github.inni.aetherflow.execution.TaskQueueClaimService;
import io.github.inni.aetherflow.execution.TimeoutRecoveryScheduler;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.entity.WorkerEntity;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import io.github.inni.aetherflow.persistence.repository.WorkerHeartbeatRepository;
import io.github.inni.aetherflow.persistence.repository.WorkerRepository;
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
@Import({TestcontainersConfiguration.class, WorkerHeartbeatIntegrationTest.HeartbeatWorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class WorkerHeartbeatIntegrationTest {

	@Autowired
	private WorkerRepository workerRepository;

	@Autowired
	private WorkerHeartbeatRepository workerHeartbeatRepository;

	@Autowired
	private TaskPoller taskPoller;

	@Autowired
	private TimeoutRecoveryScheduler timeoutRecoveryScheduler;

	@Autowired
	private WorkflowEngine workflowEngine;

	@Autowired
	private TaskQueueClaimService taskQueueClaimService;

	@Autowired
	private TaskQueueRepository taskQueueRepository;

	@Autowired
	private StepRunRepository stepRunRepository;

	@Test
	void workerRegistersOnStartup() {
		List<WorkerEntity> activeWorkers = workerRepository.findByStatusAndLastHeartbeatBefore(
			"active",
			OffsetDateTime.now().plusSeconds(10)
		);
		assertFalse(activeWorkers.isEmpty(), "Expected at least one active worker to be registered");

		WorkerEntity worker = activeWorkers.stream()
			.filter(w -> "active".equals(w.getStatus()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No active worker found"));

		assertNotNull(worker.getLastHeartbeat(), "Worker lastHeartbeat should not be null");
		assertTrue(
			worker.getLastHeartbeat().isAfter(OffsetDateTime.now().minusSeconds(10)),
			"Worker lastHeartbeat should be within the last 10 seconds"
		);
		assertEquals("active", worker.getStatus());
	}

	@Test
	void heartbeatUpdatesLastHeartbeatAndRecordsEntry() {
		// Find the registered worker
		List<WorkerEntity> activeWorkers = workerRepository.findByStatusAndLastHeartbeatBefore(
			"active",
			OffsetDateTime.now().plusSeconds(10)
		);
		assertFalse(activeWorkers.isEmpty(), "Expected at least one active worker");
		WorkerEntity worker = activeWorkers.get(0);
		String workerId = worker.getId();

		OffsetDateTime beforeHeartbeat = worker.getLastHeartbeat();
		long heartbeatCountBefore = workerHeartbeatRepository.findAll().stream()
			.filter(h -> workerId.equals(h.getWorkerId()))
			.count();

		taskPoller.heartbeat();

		WorkerEntity updated = workerRepository.findById(workerId).orElseThrow();
		assertTrue(
			!updated.getLastHeartbeat().isBefore(beforeHeartbeat),
			"lastHeartbeat should have moved forward or stayed the same"
		);

		long heartbeatCountAfter = workerHeartbeatRepository.findAll().stream()
			.filter(h -> workerId.equals(h.getWorkerId()))
			.count();
		assertEquals(heartbeatCountBefore + 1, heartbeatCountAfter, "A new WorkerHeartbeatEntity row should have been created");
	}

	@Test
	void deadWorkerRecoveryReenqueuesOrphanedTasks() throws Exception {
		UUID runId = workflowEngine.start("heartbeat-test-workflow");

		// Claim the task to put it in 'running' state
		TaskQueueEntity task = taskQueueClaimService.claimNextTask("test-worker-hb", 30).orElseThrow();

		// Find the registered worker and set its last_heartbeat to 60 seconds ago
		List<WorkerEntity> activeWorkers = workerRepository.findByStatusAndLastHeartbeatBefore(
			"active",
			OffsetDateTime.now().plusSeconds(10)
		);
		assertFalse(activeWorkers.isEmpty(), "Expected at least one active worker");
		WorkerEntity worker = activeWorkers.stream()
			.filter(w -> "active".equals(w.getStatus()))
			.findFirst()
			.orElseThrow();

		worker.setLastHeartbeat(OffsetDateTime.now().minusSeconds(60));
		workerRepository.save(worker);

		// Also update the task's worker_id to the worker being tested so it gets recovered
		task.setWorkerId(worker.getId());
		taskQueueRepository.save(task);

		// Trigger dead worker recovery
		timeoutRecoveryScheduler.recoverDeadWorkers();

		// Assert the worker is now marked as dead
		WorkerEntity deadWorker = workerRepository.findById(worker.getId()).orElseThrow();
		assertEquals("dead", deadWorker.getStatus(), "Worker should now be marked as 'dead'");

		// Assert a new pending task was enqueued for the same (workflow_run_id, step_name) (retry path)
		List<TaskQueueEntity> tasks = taskQueueRepository.findByWorkflowRunId(runId);
		long pendingCount = tasks.stream()
			.filter(t -> "pending".equals(t.getStatus()))
			.filter(t -> "hb-step".equals(t.getStepName()))
			.count();
		assertEquals(1, pendingCount, "Expected a new pending retry task to be enqueued after dead worker recovery");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class HeartbeatWorkflowConfig {

		@Bean
		HeartbeatTestWorkflow heartbeatTestWorkflow() {
			return new HeartbeatTestWorkflow();
		}
	}

	@AIWorkflow("heartbeat-test-workflow")
	static class HeartbeatTestWorkflow {

		@Step(name = "hb-step", retries = 1)
		void hbStep() {
			// no-op — never actually executed in these tests
		}
	}
}
