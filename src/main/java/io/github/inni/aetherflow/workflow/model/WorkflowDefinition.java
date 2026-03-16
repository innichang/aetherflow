package io.github.inni.aetherflow.workflow.model;

import java.util.List;

public record WorkflowDefinition(
	String workflowName,
	String beanName,
	String className,
	List<StepDefinition> steps
) {
}

