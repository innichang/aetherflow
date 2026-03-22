package io.github.inni.aetherflow.execution;

import io.github.inni.aetherflow.engine.ExecutionStatus;
import io.github.inni.aetherflow.persistence.entity.StepEntity;
import org.springframework.dao.DataIntegrityViolationException;
import io.github.inni.aetherflow.persistence.entity.StepRunEntity;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.entity.WorkflowRunEntity;
import io.github.inni.aetherflow.persistence.repository.StepRepository;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.persistence.service.WorkflowMetadataSyncService;
import io.github.inni.aetherflow.workflow.registry.RegisteredWorkflow;
import io.github.inni.aetherflow.workflow.registry.WorkflowRegistry;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultReporter {

	private final StepRunRepository stepRunRepository;
	private final TaskQueueRepository taskQueueRepository;
	private final WorkflowRunRepository workflowRunRepository;
	private final StepRepository stepRepository;
	private final WorkflowRepository workflowRepository;
	private final WorkflowRegistry workflowRegistry;
	private final WorkflowMetadataSyncService metadataSyncService;

	public ResultReporter(
		StepRunRepository stepRunRepository,
		TaskQueueRepository taskQueueRepository,
		WorkflowRunRepository workflowRunRepository,
		StepRepository stepRepository,
		WorkflowRepository workflowRepository,
		WorkflowRegistry workflowRegistry,
		WorkflowMetadataSyncService metadataSyncService
	) {
		this.stepRunRepository = stepRunRepository;
		this.taskQueueRepository = taskQueueRepository;
		this.workflowRunRepository = workflowRunRepository;
		this.stepRepository = stepRepository;
		this.workflowRepository = workflowRepository;
		this.workflowRegistry = workflowRegistry;
		this.metadataSyncService = metadataSyncService;
	}

	@Transactional
	public void report(TaskQueueEntity task, ExecutionResult result) {
		StepRunEntity stepRun = stepRunRepository.findById(task.getStepRunId()).orElseThrow();
		WorkflowRunEntity workflowRun = workflowRunRepository.findById(task.getWorkflowRunId()).orElseThrow();

		if (result.success()) {
			markStepAndTaskCompleted(stepRun, task, result.durationMs());
			enqueueReadyDependents(workflowRun, task.getStepName());
			maybeCompleteWorkflow(workflowRun);
			return;
		}

		int retryCount = stepRun.getRetryCount();
		int maxRetries = stepRun.getMaxRetries();

		if (retryCount < maxRetries) {
			scheduleRetry(stepRun, task, result, workflowRun.getWorkflowName());
			return;
		}

		markStepAndTaskFailed(stepRun, task, result);
		workflowRun.setStatus(ExecutionStatus.FAILED);
		workflowRun.setErrorMessage(result.error().getMessage());
		workflowRun.setCompletedAt(OffsetDateTime.now());
		workflowRunRepository.save(workflowRun);
	}

	private void scheduleRetry(StepRunEntity stepRun, TaskQueueEntity task, ExecutionResult result, String workflowName) {
		int retryCount = stepRun.getRetryCount();
		int backoffSeconds = resolveBackoffSeconds(stepRun, workflowName);
		// exponential backoff: delay = backoffSeconds * 2^retryCount (minimum 0)
		long delaySeconds = (long) backoffSeconds * (1L << retryCount);
		OffsetDateTime nextRetryTime = OffsetDateTime.now().plusSeconds(delaySeconds);

		stepRun.setStatus(ExecutionStatus.RETRYING);
		stepRun.setRetryCount(retryCount + 1);
		stepRun.setNextRetryTime(nextRetryTime);
		stepRun.setDurationMs(result.durationMs());
		stepRun.setErrorType(result.error().getClass().getName());
		stepRun.setErrorMessage(result.error().getMessage());
		stepRun.setCompletedAt(OffsetDateTime.now());
		stepRunRepository.save(stepRun);

		task.setStatus(ExecutionStatus.FAILED);
		task.setLockedAt(OffsetDateTime.now());
		taskQueueRepository.save(task);

		StepRunEntity retryStepRun = new StepRunEntity();
		retryStepRun.setId(UUID.randomUUID());
		retryStepRun.setWorkflowRunId(stepRun.getWorkflowRunId());
		retryStepRun.setWorkflowId(stepRun.getWorkflowId());
		retryStepRun.setStepId(stepRun.getStepId());
		retryStepRun.setStepName(stepRun.getStepName());
		retryStepRun.setStatus(ExecutionStatus.PENDING);
		retryStepRun.setAttempt(stepRun.getAttempt() + 1);
		retryStepRun.setMaxRetries(stepRun.getMaxRetries());
		retryStepRun.setRetryCount(retryCount + 1);
		retryStepRun.setIdempotencyKey(stepRun.getWorkflowRunId() + ":" + stepRun.getStepName() + ":" + (stepRun.getAttempt() + 1));
		try {
			stepRunRepository.save(retryStepRun);
		} catch (DataIntegrityViolationException e) {
			// retry step run already exists — skip re-enqueue
			return;
		}

		TaskQueueEntity retryTask = new TaskQueueEntity();
		retryTask.setId(UUID.randomUUID());
		retryTask.setWorkflowRunId(stepRun.getWorkflowRunId());
		retryTask.setStepRunId(retryStepRun.getId());
		retryTask.setWorkflowName(task.getWorkflowName());
		retryTask.setStepName(stepRun.getStepName());
		retryTask.setStatus(ExecutionStatus.PENDING);
		retryTask.setPriority(task.getPriority());
		retryTask.setAvailableAt(nextRetryTime);
		taskQueueRepository.save(retryTask);
	}

	private int resolveBackoffSeconds(StepRunEntity stepRun, String workflowName) {
		UUID workflowId = workflowRepository.findByWorkflowName(workflowName).orElseThrow().getId();
		return stepRepository.findByWorkflowIdAndStepName(workflowId, stepRun.getStepName())
			.map(StepEntity::getBackoffSeconds)
			.orElse(0);
	}

	private void markStepAndTaskCompleted(StepRunEntity stepRun, TaskQueueEntity task, long durationMs) {
		OffsetDateTime now = OffsetDateTime.now();
		stepRun.setStatus(ExecutionStatus.COMPLETED);
		stepRun.setDurationMs(durationMs);
		stepRun.setCompletedAt(now);
		stepRunRepository.save(stepRun);

		task.setStatus(ExecutionStatus.COMPLETED);
		task.setLockedAt(now);
		taskQueueRepository.save(task);
	}

	private void markStepAndTaskFailed(StepRunEntity stepRun, TaskQueueEntity task, ExecutionResult result) {
		OffsetDateTime now = OffsetDateTime.now();
		stepRun.setStatus(ExecutionStatus.FAILED);
		stepRun.setDurationMs(result.durationMs());
		stepRun.setCompletedAt(now);
		stepRun.setErrorType(result.error().getClass().getName());
		stepRun.setErrorMessage(result.error().getMessage());
		stepRunRepository.save(stepRun);

		task.setStatus(ExecutionStatus.FAILED);
		task.setLockedAt(now);
		taskQueueRepository.save(task);
	}

	private void enqueueReadyDependents(WorkflowRunEntity workflowRun, String completedStepName) {
		RegisteredWorkflow registeredWorkflow = workflowRegistry.getRequired(workflowRun.getWorkflowName());
		metadataSyncService.sync(registeredWorkflow);
		UUID workflowId = workflowRepository.findByWorkflowName(workflowRun.getWorkflowName())
			.orElseThrow()
			.getId();
		Map<String, StepEntity> stepsByName = new HashMap<>();
		for (StepEntity step : stepRepository.findByWorkflowIdOrderByStepOrderHintAsc(workflowId)) {
			stepsByName.put(step.getStepName(), step);
		}

		for (String dependentName : registeredWorkflow.dependencyGraph().dependentsOf(completedStepName)) {
			List<StepRunEntity> existingRuns = stepRunRepository.findByWorkflowRunIdAndStepName(
				workflowRun.getId(),
				dependentName
			);
			if (!existingRuns.isEmpty()) {
				continue;
			}

			boolean dependenciesReady = registeredWorkflow.dependencyGraph()
				.dependenciesOf(dependentName)
				.stream()
				.allMatch(dependency -> stepRunRepository
					.findByWorkflowRunIdAndStepName(workflowRun.getId(), dependency)
					.stream()
					.anyMatch(run -> ExecutionStatus.COMPLETED.equals(run.getStatus()))
				);
			if (!dependenciesReady) {
				continue;
			}

			StepEntity dependentStep = stepsByName.get(dependentName);
			StepRunEntity stepRun = new StepRunEntity();
			stepRun.setId(UUID.randomUUID());
			stepRun.setWorkflowRunId(workflowRun.getId());
			stepRun.setWorkflowId(workflowId);
			stepRun.setStepId(dependentStep.getId());
			stepRun.setStepName(dependentName);
			stepRun.setStatus(ExecutionStatus.PENDING);
			stepRun.setAttempt(1);
			stepRun.setMaxRetries(dependentStep.getRetries());
			stepRun.setRetryCount(0);
			stepRun.setIdempotencyKey(workflowRun.getId() + ":" + dependentName + ":1");
			try {
				stepRunRepository.save(stepRun);
			} catch (DataIntegrityViolationException e) {
				// duplicate enqueue — another worker already created this step run; skip
				continue;
			}

			TaskQueueEntity task = new TaskQueueEntity();
			task.setId(UUID.randomUUID());
			task.setWorkflowRunId(workflowRun.getId());
			task.setStepRunId(stepRun.getId());
			task.setWorkflowName(workflowRun.getWorkflowName());
			task.setStepName(dependentName);
			task.setStatus(ExecutionStatus.PENDING);
			task.setPriority(100);
			task.setAvailableAt(OffsetDateTime.now());
			taskQueueRepository.save(task);
		}
	}

	private void maybeCompleteWorkflow(WorkflowRunEntity workflowRun) {
		RegisteredWorkflow registeredWorkflow = workflowRegistry.getRequired(workflowRun.getWorkflowName());
		boolean allCompleted = registeredWorkflow.dependencyGraph()
			.topologicalOrder()
			.stream()
			.allMatch(step -> stepRunRepository
				.findByWorkflowRunIdAndStepName(workflowRun.getId(), step)
				.stream()
				.anyMatch(run -> ExecutionStatus.COMPLETED.equals(run.getStatus()))
			);
		if (!allCompleted) {
			return;
		}
		workflowRun.setStatus(ExecutionStatus.COMPLETED);
		workflowRun.setCompletedAt(OffsetDateTime.now());
		workflowRunRepository.save(workflowRun);
	}
}
