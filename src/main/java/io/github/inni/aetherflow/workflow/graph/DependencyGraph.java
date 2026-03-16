package io.github.inni.aetherflow.workflow.graph;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record DependencyGraph(
	Map<String, Set<String>> dependenciesByStep,
	Map<String, Set<String>> dependentsByStep,
	List<String> topologicalOrder
) {

	public Set<String> dependenciesOf(String stepName) {
		return dependenciesByStep.getOrDefault(stepName, Set.of());
	}

	public Set<String> dependentsOf(String stepName) {
		return dependentsByStep.getOrDefault(stepName, Set.of());
	}

	public List<String> rootSteps() {
		return topologicalOrder.stream()
			.filter(step -> dependenciesOf(step).isEmpty())
			.toList();
	}
}

