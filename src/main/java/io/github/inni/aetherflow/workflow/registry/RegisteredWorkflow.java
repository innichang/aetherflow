package io.github.inni.aetherflow.workflow.registry;

import io.github.inni.aetherflow.workflow.graph.DependencyGraph;
import io.github.inni.aetherflow.workflow.model.WorkflowDefinition;
import java.util.Map;

public record RegisteredWorkflow(
	WorkflowDefinition definition,
	DependencyGraph dependencyGraph,
	Map<String, StepInvoker> invokersByStepName
) {
}

