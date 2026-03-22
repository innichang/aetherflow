package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@Import({TestcontainersConfiguration.class, WorkflowRunApiIntegrationTest.ApiTestWorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class WorkflowRunApiIntegrationTest {

	@Autowired
	private WebApplicationContext wac;

	@Autowired
	private ObjectMapper objectMapper;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
	}

	@Autowired
	private TaskPoller taskPoller;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Test
	void startWorkflow_returnsRunIdAndAccepted() throws Exception {
		String body = objectMapper.writeValueAsString(Map.of("workflowName", "api-test-workflow"));

		MvcResult result = mockMvc.perform(post("/workflows/start")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.runId").isNotEmpty())
			.andExpect(jsonPath("$.workflowName").value("api-test-workflow"))
			.andReturn();

		String runId = objectMapper.readTree(result.getResponse().getContentAsString()).get("runId").asText();
		assertNotNull(UUID.fromString(runId));
	}

	@Test
	void startWorkflow_unknownWorkflow_returns400() throws Exception {
		String body = objectMapper.writeValueAsString(Map.of("workflowName", "no-such-workflow"));

		mockMvc.perform(post("/workflows/start")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
	}

	@Test
	void getRun_returnsRunDetailWithSteps() throws Exception {
		String body = objectMapper.writeValueAsString(Map.of(
			"workflowName", "api-test-workflow",
			"input", Map.of("key", "value")
		));

		MvcResult startResult = mockMvc.perform(post("/workflows/start")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted())
			.andReturn();

		String runId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("runId").asText();

		for (int i = 0; i < 50; i++) {
			taskPoller.poll();
			var run = workflowRunRepository.findById(UUID.fromString(runId)).orElseThrow();
			if ("completed".equals(run.getStatus()) || "failed".equals(run.getStatus())) break;
		}

		mockMvc.perform(get("/workflow-runs/" + runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runId").value(runId))
			.andExpect(jsonPath("$.workflowName").value("api-test-workflow"))
			.andExpect(jsonPath("$.status").value("completed"))
			.andExpect(jsonPath("$.steps").isArray())
			.andExpect(jsonPath("$.steps[0].stepName").isNotEmpty());
	}

	@Test
	void getRun_unknownId_returns404() throws Exception {
		mockMvc.perform(get("/workflow-runs/" + UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	@Test
	void listRuns_filterByWorkflowName_returnsMatchingRuns() throws Exception {
		String body = objectMapper.writeValueAsString(Map.of("workflowName", "api-test-workflow"));
		mockMvc.perform(post("/workflows/start")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted());

		mockMvc.perform(get("/workflow-runs")
				.param("workflowName", "api-test-workflow"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isArray())
			.andExpect(jsonPath("$.content[0].workflowName").value("api-test-workflow"))
			.andExpect(jsonPath("$.totalElements").isNumber());
	}

	@TestConfiguration
	static class ApiTestWorkflowConfig {

		@Bean
		ApiTestWorkflow apiTestWorkflow() {
			return new ApiTestWorkflow();
		}
	}

	@AIWorkflow("api-test-workflow")
	static class ApiTestWorkflow {

		@Step
		public void stepA() {}
	}
}
