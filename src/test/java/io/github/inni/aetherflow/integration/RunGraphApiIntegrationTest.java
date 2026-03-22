package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.execution.TaskPoller;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RunGraphApiIntegrationTest.GraphApiConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class RunGraphApiIntegrationTest {

	@Autowired
	private WebApplicationContext wac;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TaskPoller taskPoller;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
	}

	@Test
	void getRunGraph_unknownRunId_returns404() throws Exception {
		mockMvc.perform(get("/workflow-runs/" + UUID.randomUUID() + "/graph"))
			.andExpect(status().isNotFound());
	}

	@Test
	void getRunGraph_afterCompletion_returnsAllStepsCompleted() throws Exception {
		// Start the workflow
		String body = objectMapper.writeValueAsString(Map.of("workflowName", "graph-api-workflow"));
		MvcResult startResult = mockMvc.perform(post("/workflows/start")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted())
			.andReturn();

		String runId = objectMapper.readTree(startResult.getResponse().getContentAsString())
			.get("runId").asText();
		assertNotNull(runId);

		// Drive execution to completion
		for (int i = 0; i < 50; i++) {
			taskPoller.poll();
			var run = workflowRunRepository.findById(UUID.fromString(runId)).orElseThrow();
			if ("completed".equals(run.getStatus()) || "failed".equals(run.getStatus())) break;
		}

		// Verify graph endpoint
		MvcResult graphResult = mockMvc.perform(get("/workflow-runs/" + runId + "/graph"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runId").value(runId))
			.andExpect(jsonPath("$.workflowName").value("graph-api-workflow"))
			.andExpect(jsonPath("$.status").value("completed"))
			.andExpect(jsonPath("$.steps").isArray())
			.andExpect(jsonPath("$.steps.length()").value(3))
			.andReturn();

		JsonNode steps = objectMapper.readTree(graphResult.getResponse().getContentAsString()).get("steps");

		// All steps must be completed
		for (JsonNode step : steps) {
			assertEquals("completed", step.get("status").asText(),
				"Step " + step.get("name").asText() + " should be completed");
			assertNotNull(step.get("durationMs"),
				"Step " + step.get("name").asText() + " should have a durationMs");
		}

		// Root step has no dependencies
		assertEquals(0, steps.get(0).get("dependsOn").size());
		// Downstream steps carry their dependsOn list
		assertEquals("stepA", steps.get(1).get("dependsOn").get(0).asText());
		assertEquals("stepB", steps.get(2).get("dependsOn").get(0).asText());
	}

	@Test
	void getRunGraph_pendingSteps_showNotStartedStatus() throws Exception {
		// Start but do NOT drive execution — leaf steps will be not_started
		String body = objectMapper.writeValueAsString(Map.of("workflowName", "graph-api-workflow"));
		MvcResult startResult = mockMvc.perform(post("/workflows/start")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted())
			.andReturn();

		String runId = objectMapper.readTree(startResult.getResponse().getContentAsString())
			.get("runId").asText();

		// Only drive one poll so root step starts; downstream steps remain not_started
		taskPoller.poll();

		MvcResult graphResult = mockMvc.perform(get("/workflow-runs/" + runId + "/graph"))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode steps = objectMapper.readTree(graphResult.getResponse().getContentAsString()).get("steps");

		// At least the leaf step (stepC) should still be not_started or pending
		JsonNode lastStep = steps.get(steps.size() - 1);
		String lastStatus = lastStep.get("status").asText();
		assert "not_started".equals(lastStatus) || "pending".equals(lastStatus)
			: "Expected last step to be not_started or pending, got: " + lastStatus;
	}

	// --- test fixtures ---

	@TestConfiguration
	static class GraphApiConfig {

		@Bean
		GraphApiWorkflow graphApiWorkflow() {
			return new GraphApiWorkflow();
		}
	}

	@AIWorkflow("graph-api-workflow")
	static class GraphApiWorkflow {

		@Step(name = "stepA")
		public void stepA() {}

		@Step(name = "stepB", dependsOn = "stepA")
		public void stepB() {}

		@Step(name = "stepC", dependsOn = "stepB")
		public void stepC() {}
	}
}
