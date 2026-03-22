package io.github.inni.aetherflow.engine;

import io.github.inni.aetherflow.persistence.entity.StepEntity;
import io.github.inni.aetherflow.persistence.entity.StepRunEntity;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.entity.WorkflowEntity;
import io.github.inni.aetherflow.persistence.entity.WorkflowRunEntity;
import io.github.inni.aetherflow.persistence.repository.StepRepository;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.persistence.service.WorkflowMetadataSyncService;
import io.github.inni.aetherflow.workflow.registry.RegisteredWorkflow;
import io.github.inni.aetherflow.workflow.registry.WorkflowRegistry;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowEngine {

	private final WorkflowRegistry workflowRegistry;
	private final WorkflowMetadataSyncService metadataSyncService;
	private final WorkflowRunRepository workflowRunRepository;
	private final StepRepository stepRepository;
	private final StepRunRepository stepRunRepository;
	private final TaskQueueRepository taskQueueRepository;

	public WorkflowEngine(
		WorkflowRegistry workflowRegistry,
		WorkflowMetadataSyncService metadataSyncService,
		WorkflowRunRepository workflowRunRepository,
		StepRepository stepRepository,
		StepRunRepository stepRunRepository,
		TaskQueueRepository taskQueueRepository
	) {
		this.workflowRegistry = workflowRegistry;
		this.metadataSyncService = metadataSyncService;
		this.workflowRunRepository = workflowRunRepository;
		this.stepRepository = stepRepository;
		this.stepRunRepository = stepRunRepository;
		this.taskQueueRepository = taskQueueRepository;
	}

	@Transactional
	public UUID start(String workflowName) {
		return start(workflowName, null);
	}

	@Transactional
	public UUID start(String workflowName, String inputPayload) {
		RegisteredWorkflow registeredWorkflow = workflowRegistry.getRequired(workflowName);
		WorkflowEntity workflowEntity = metadataSyncService.sync(registeredWorkflow);
		Map<String, StepEntity> stepEntityMap = new HashMap<>();
		for (StepEntity stepEntity : stepRepository.findByWorkflowIdOrderByStepOrderHintAsc(workflowEntity.getId())) {
			stepEntityMap.put(stepEntity.getStepName(), stepEntity);
		}

		WorkflowRunEntity workflowRun = new WorkflowRunEntity();
		workflowRun.setId(UUID.randomUUID());
		workflowRun.setWorkflowId(workflowEntity.getId());
		workflowRun.setWorkflowName(workflowEntity.getWorkflowName());
		workflowRun.setWorkflowVersion(workflowEntity.getWorkflowVersion());
		workflowRun.setStatus(ExecutionStatus.RUNNING);
		workflowRun.setInputPayload(inputPayload);
		workflowRun.setStartedAt(OffsetDateTime.now());
		workflowRunRepository.save(workflowRun);

		for (String rootStep : registeredWorkflow.dependencyGraph().rootSteps()) {
			StepEntity stepEntity = stepEntityMap.get(rootStep);
			if (stepEntity == null) {
				throw new WorkflowRuntimeException(
					"Step '%s' was not found in metadata for workflow '%s'".formatted(rootStep, workflowName)
				);
			}
			StepRunEntity stepRun = new StepRunEntity();
			stepRun.setId(UUID.randomUUID());
			stepRun.setWorkflowRunId(workflowRun.getId());
			stepRun.setWorkflowId(workflowEntity.getId());
			stepRun.setStepId(stepEntity.getId());
			stepRun.setStepName(stepEntity.getStepName());
			stepRun.setStatus(ExecutionStatus.PENDING);
			stepRun.setAttempt(1);
			stepRun.setMaxRetries(stepEntity.getRetries());
			stepRun.setRetryCount(0);
			stepRun.setIdempotencyKey(workflowRun.getId() + ":" + stepEntity.getStepName() + ":1");
			stepRunRepository.save(stepRun);

			TaskQueueEntity task = new TaskQueueEntity();
			task.setId(UUID.randomUUID());
			task.setWorkflowRunId(workflowRun.getId());
			task.setStepRunId(stepRun.getId());
			task.setWorkflowName(workflowRun.getWorkflowName());
			task.setStepName(stepEntity.getStepName());
			task.setStatus(ExecutionStatus.PENDING);
			task.setPriority(100);
			task.setAvailableAt(OffsetDateTime.now());
			taskQueueRepository.save(task);
		}

		return workflowRun.getId();
	}
}

