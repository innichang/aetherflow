package io.github.inni.aetherflow.workflow.registry;

import io.github.inni.aetherflow.workflow.graph.DependencyGraph;
import io.github.inni.aetherflow.workflow.graph.DependencyGraphBuilder;
import io.github.inni.aetherflow.workflow.registry.WorkflowScanner.ScannedWorkflow;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class WorkflowRegistry {

	private final WorkflowScanner workflowScanner;
	private final DependencyGraphBuilder dependencyGraphBuilder;
	private final Map<String, RegisteredWorkflow> registry = new ConcurrentHashMap<>();

	public WorkflowRegistry(WorkflowScanner workflowScanner, DependencyGraphBuilder dependencyGraphBuilder) {
		this.workflowScanner = workflowScanner;
		this.dependencyGraphBuilder = dependencyGraphBuilder;
	}

	@PostConstruct
	public void initialize() {
		registry.clear();
		for (ScannedWorkflow scannedWorkflow : workflowScanner.scan()) {
			DependencyGraph graph = dependencyGraphBuilder.build(scannedWorkflow.definition());
			RegisteredWorkflow registeredWorkflow = new RegisteredWorkflow(
				scannedWorkflow.definition(),
				graph,
				scannedWorkflow.invokersByStepName()
			);
			registry.put(scannedWorkflow.definition().workflowName(), registeredWorkflow);
		}
	}

	public Optional<RegisteredWorkflow> find(String workflowName) {
		return Optional.ofNullable(registry.get(workflowName));
	}

	public RegisteredWorkflow getRequired(String workflowName) {
		return find(workflowName)
			.orElseThrow(() -> new IllegalArgumentException("Workflow '%s' not found".formatted(workflowName)));
	}

	public Collection<RegisteredWorkflow> all() {
		return registry.values();
	}
}

