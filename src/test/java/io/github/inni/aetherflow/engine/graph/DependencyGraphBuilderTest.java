package io.github.inni.aetherflow.engine.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.inni.aetherflow.workflow.graph.DependencyGraph;
import io.github.inni.aetherflow.workflow.graph.DependencyGraphBuilder;
import io.github.inni.aetherflow.workflow.graph.WorkflowValidationException;
import io.github.inni.aetherflow.workflow.model.StepDefinition;
import io.github.inni.aetherflow.workflow.model.WorkflowDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyGraphBuilderTest {

	private final DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder();

	@Test
	void buildsTopologicalOrderForLinearDag() {
		WorkflowDefinition workflow = new WorkflowDefinition(
			"document-pipeline",
			"documentWorkflow",
			"DocumentWorkflow",
			List.of(
				new StepDefinition("extractText", "extractText", List.of(), 0, 0, null, null),
				new StepDefinition("chunkText", "chunkText", List.of("extractText"), 0, 0, null, null),
				new StepDefinition("embedChunks", "embedChunks", List.of("chunkText"), 0, 0, null, null)
			)
		);

		DependencyGraph graph = dependencyGraphBuilder.build(workflow);

		assertEquals(List.of("extractText", "chunkText", "embedChunks"), graph.topologicalOrder());
		assertEquals(List.of("extractText"), graph.rootSteps());
	}

	@Test
	void failsWhenDependencyIsMissing() {
		WorkflowDefinition workflow = new WorkflowDefinition(
			"invalid-workflow",
			"invalidWorkflow",
			"InvalidWorkflow",
			List.of(new StepDefinition("chunkText", "chunkText", List.of("extractText"), 0, 0, null, null))
		);

		assertThrows(WorkflowValidationException.class, () -> dependencyGraphBuilder.build(workflow));
	}

	@Test
	void failsOnCircularDependency() {
		WorkflowDefinition workflow = new WorkflowDefinition(
			"cycle-workflow",
			"cycleWorkflow",
			"CycleWorkflow",
			List.of(
				new StepDefinition("stepA", "stepA", List.of("stepB"), 0, 0, null, null),
				new StepDefinition("stepB", "stepB", List.of("stepA"), 0, 0, null, null)
			)
		);

		assertThrows(WorkflowValidationException.class, () -> dependencyGraphBuilder.build(workflow));
	}

	@Test
	void failsOnDuplicateStepName() {
		WorkflowDefinition workflow = new WorkflowDefinition(
			"dup-workflow",
			"dupWorkflow",
			"DupWorkflow",
			List.of(
				new StepDefinition("extractText", "extractText", List.of(), 0, 0, null, null),
				new StepDefinition("extractText", "extractTextAgain", List.of(), 0, 0, null, null)
			)
		);

		assertThrows(WorkflowValidationException.class, () -> dependencyGraphBuilder.build(workflow));
	}
}

