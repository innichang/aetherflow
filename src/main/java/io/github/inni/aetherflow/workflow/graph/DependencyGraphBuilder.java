package io.github.inni.aetherflow.workflow.graph;

import io.github.inni.aetherflow.workflow.model.StepDefinition;
import io.github.inni.aetherflow.workflow.model.WorkflowDefinition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DependencyGraphBuilder {

	public DependencyGraph build(WorkflowDefinition workflowDefinition) {
		Map<String, StepDefinition> stepsByName = new LinkedHashMap<>();
		for (StepDefinition step : workflowDefinition.steps()) {
			if (stepsByName.containsKey(step.stepName())) {
				throw new WorkflowValidationException(
					"Duplicate step name '%s' in workflow '%s'".formatted(step.stepName(), workflowDefinition.workflowName())
				);
			}
			stepsByName.put(step.stepName(), step);
		}

		Map<String, Set<String>> dependenciesByStep = new HashMap<>();
		Map<String, Set<String>> dependentsByStep = new HashMap<>();
		Map<String, Integer> inDegree = new HashMap<>();

		for (String stepName : stepsByName.keySet()) {
			dependenciesByStep.put(stepName, new HashSet<>());
			dependentsByStep.put(stepName, new HashSet<>());
			inDegree.put(stepName, 0);
		}

		for (StepDefinition step : workflowDefinition.steps()) {
			for (String dependency : step.dependsOn()) {
				if (!stepsByName.containsKey(dependency)) {
					throw new WorkflowValidationException(
						"Step '%s' depends on missing step '%s' in workflow '%s'"
							.formatted(step.stepName(), dependency, workflowDefinition.workflowName())
					);
				}
				dependenciesByStep.get(step.stepName()).add(dependency);
				dependentsByStep.get(dependency).add(step.stepName());
				inDegree.put(step.stepName(), inDegree.get(step.stepName()) + 1);
			}
		}

		ArrayDeque<String> queue = new ArrayDeque<>(
			inDegree.entrySet()
				.stream()
				.filter(entry -> entry.getValue() == 0)
				.map(Map.Entry::getKey)
				.sorted(Comparator.naturalOrder())
				.toList()
		);
		List<String> topologicalOrder = new ArrayList<>();

		while (!queue.isEmpty()) {
			String current = queue.removeFirst();
			topologicalOrder.add(current);
			List<String> dependents = dependentsByStep.getOrDefault(current, Set.of())
				.stream()
				.sorted()
				.toList();
			for (String dependent : dependents) {
				int next = inDegree.get(dependent) - 1;
				inDegree.put(dependent, next);
				if (next == 0) {
					queue.addLast(dependent);
				}
			}
		}

		if (topologicalOrder.size() != stepsByName.size()) {
			throw new WorkflowValidationException(
				"Circular dependency detected in workflow '%s'".formatted(workflowDefinition.workflowName())
			);
		}

		return new DependencyGraph(
			dependenciesByStep.entrySet().stream().collect(
				LinkedHashMap::new,
				(map, entry) -> map.put(entry.getKey(), Set.copyOf(entry.getValue())),
				Map::putAll
			),
			dependentsByStep.entrySet().stream().collect(
				LinkedHashMap::new,
				(map, entry) -> map.put(entry.getKey(), Set.copyOf(entry.getValue())),
				Map::putAll
			),
			List.copyOf(topologicalOrder)
		);
	}
}

