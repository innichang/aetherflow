package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import io.github.inni.aetherflow.execution.TaskPoller;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, WorkflowExecutionOrderIntegrationTest.WorkflowTestConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class WorkflowExecutionOrderIntegrationTest {

	@Autowired
	private WorkflowEngine workflowEngine;

	@Autowired
	private TaskPoller taskPoller;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Autowired
	private StepRunRepository stepRunRepository;

	@BeforeEach
	void setUp() {
		ExecutionTracker.EVENTS.clear();
	}

	@Test
	void executesDagInDependencyOrderAndPersistsRunState() throws Exception {
		UUID runId = workflowEngine.start("tracked-workflow");
		assertNotNull(runId);

		for (int i = 0; i < 100; i++) {
			taskPoller.poll();
			var run = workflowRunRepository.findById(runId).orElseThrow();
			if ("completed".equals(run.getStatus())) {
				break;
			}
			Thread.sleep(20);
		}

		var completedRun = workflowRunRepository.findById(runId).orElseThrow();
		assertEquals("completed", completedRun.getStatus());
		assertEquals(List.of("extract", "chunk", "embed"), ExecutionTracker.EVENTS);

		var stepRuns = stepRunRepository.findByWorkflowRunId(runId);
		assertEquals(3, stepRuns.size());
		assertTrue(stepRuns.stream().allMatch(stepRun -> "completed".equals(stepRun.getStatus())));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class WorkflowTestConfig {

		@Bean
		TrackedWorkflow trackedWorkflow() {
			return new TrackedWorkflow();
		}
	}

	@AIWorkflow("tracked-workflow")
	static class TrackedWorkflow {

		@Step(name = "extract")
		void extract() {
			ExecutionTracker.EVENTS.add("extract");
		}

		@Step(name = "chunk", dependsOn = {"extract"})
		void chunk() {
			ExecutionTracker.EVENTS.add("chunk");
		}

		@Step(name = "embed", dependsOn = {"chunk"})
		void embed() {
			ExecutionTracker.EVENTS.add("embed");
		}
	}

	static class ExecutionTracker {

		static final CopyOnWriteArrayList<String> EVENTS = new CopyOnWriteArrayList<>();
	}
}

