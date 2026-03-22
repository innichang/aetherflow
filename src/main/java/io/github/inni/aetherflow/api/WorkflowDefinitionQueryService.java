package io.github.inni.aetherflow.api;

import io.github.inni.aetherflow.workflow.model.StepDefinition;
import io.github.inni.aetherflow.workflow.registry.RegisteredWorkflow;
import io.github.inni.aetherflow.workflow.registry.WorkflowRegistry;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "aetherflow.api.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowDefinitionQueryService {

	private final WorkflowRegistry workflowRegistry;

	public WorkflowDefinitionQueryService(WorkflowRegistry workflowRegistry) {
		this.workflowRegistry = workflowRegistry;
	}

	public List<WorkflowSummary> listWorkflows() {
		return workflowRegistry.all().stream()
			.map(w -> new WorkflowSummary(
				w.definition().workflowName(),
				w.definition().steps().size()
			))
			.sorted((a, b) -> a.workflowName().compareTo(b.workflowName()))
			.toList();
	}

	public WorkflowDefinitionResponse getDefinition(String workflowName) {
		RegisteredWorkflow w = workflowRegistry.find(workflowName)
			.orElseThrow(() -> new NoSuchElementException("Workflow '%s' not found".formatted(workflowName)));

		List<StepNode> steps = w.dependencyGraph().topologicalOrder().stream()
			.map(stepName -> {
				StepDefinition def = w.definition().steps().stream()
					.filter(s -> s.stepName().equals(stepName))
					.findFirst()
					.orElseThrow();
				return new StepNode(
					def.stepName(),
					def.dependsOn(),
					def.retries(),
					def.backoffSeconds(),
					def.timeoutSeconds(),
					def.concurrencyLimit()
				);
			})
			.toList();

		return new WorkflowDefinitionResponse(workflowName, steps);
	}

	// --- response records ---

	public record WorkflowSummary(String workflowName, int stepCount) {}

	public record WorkflowDefinitionResponse(String workflowName, List<StepNode> steps) {}

	public record StepNode(
		String name,
		List<String> dependsOn,
		int retries,
		int backoffSeconds,
		Integer timeoutSeconds,
		Integer concurrencyLimit
	) {}
}
