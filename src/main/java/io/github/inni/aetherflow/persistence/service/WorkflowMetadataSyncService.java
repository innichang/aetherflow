package io.github.inni.aetherflow.persistence.service;

import io.github.inni.aetherflow.persistence.entity.StepEntity;
import io.github.inni.aetherflow.persistence.entity.WorkflowEntity;
import io.github.inni.aetherflow.persistence.repository.StepRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRepository;
import io.github.inni.aetherflow.workflow.model.StepDefinition;
import io.github.inni.aetherflow.workflow.registry.RegisteredWorkflow;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowMetadataSyncService {

	private final WorkflowRepository workflowRepository;
	private final StepRepository stepRepository;

	public WorkflowMetadataSyncService(WorkflowRepository workflowRepository, StepRepository stepRepository) {
		this.workflowRepository = workflowRepository;
		this.stepRepository = stepRepository;
	}

	@Transactional
	public WorkflowEntity sync(RegisteredWorkflow registeredWorkflow) {
		WorkflowEntity workflowEntity = workflowRepository.findByWorkflowName(registeredWorkflow.definition().workflowName())
			.orElseGet(() -> {
				WorkflowEntity created = new WorkflowEntity();
				created.setId(UUID.randomUUID());
				created.setWorkflowName(registeredWorkflow.definition().workflowName());
				created.setWorkflowVersion(1);
				return created;
			});

		workflowEntity.setBeanName(registeredWorkflow.definition().beanName());
		workflowEntity.setClassName(registeredWorkflow.definition().className());
		workflowEntity = workflowRepository.save(workflowEntity);
		UUID workflowId = workflowEntity.getId();

		Set<String> seenSteps = new HashSet<>();
		int order = 0;
		for (StepDefinition definition : registeredWorkflow.definition().steps()) {
			StepEntity stepEntity = stepRepository.findByWorkflowIdAndStepName(workflowEntity.getId(), definition.stepName())
				.orElseGet(() -> {
					StepEntity created = new StepEntity();
					created.setId(UUID.randomUUID());
					created.setWorkflowId(workflowId);
					created.setStepName(definition.stepName());
					return created;
				});
			stepEntity.setMethodName(definition.methodName());
			stepEntity.setStepOrderHint(order++);
			stepEntity.setDependsOn(String.join(",", definition.dependsOn()));
			stepEntity.setRetries(definition.retries());
			stepEntity.setBackoffSeconds(definition.backoffSeconds());
			stepEntity.setTimeoutSeconds(definition.timeoutSeconds());
			stepEntity.setConcurrencyLimit(definition.concurrencyLimit());
			stepRepository.save(stepEntity);
			seenSteps.add(stepEntity.getStepName());
		}

		for (StepEntity existingStep : stepRepository.findByWorkflowIdOrderByStepOrderHintAsc(workflowEntity.getId())) {
			if (!seenSteps.contains(existingStep.getStepName())) {
				stepRepository.delete(existingStep);
			}
		}

		return workflowEntity;
	}
}

