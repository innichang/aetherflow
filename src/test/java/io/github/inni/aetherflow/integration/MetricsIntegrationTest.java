package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@Import({TestcontainersConfiguration.class, MetricsIntegrationTest.MetricsConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class MetricsIntegrationTest {

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
	void prometheusEndpoint_isAccessibleAndExposesGaugeMetrics() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andReturn();

		String body = result.getResponse().getContentAsString();
		assertTrue(body.contains("aetherflow_queue_depth"),
			"Expected aetherflow_queue_depth gauge in /actuator/prometheus");
		assertTrue(body.contains("aetherflow_workers_active"),
			"Expected aetherflow_workers_active gauge in /actuator/prometheus");
	}

	@Test
	void prometheusEndpoint_afterWorkflowRun_exposesCountersAndTimer() throws Exception {
		// Start and drive a workflow to completion
		String body = objectMapper.writeValueAsString(Map.of("workflowName", "metrics-test-workflow"));
		mockMvc.perform(post("/workflows/start")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted());

		// Temporarily enable frequent polling to drive execution
		for (int i = 0; i < 50; i++) {
			taskPoller.poll();
			// Check if any run of this workflow is complete
			boolean done = workflowRunRepository.findAll().stream()
				.anyMatch(r -> "metrics-test-workflow".equals(r.getWorkflowName())
					&& ("completed".equals(r.getStatus()) || "failed".equals(r.getStatus())));
			if (done) break;
		}

		MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andReturn();

		String prometheus = result.getResponse().getContentAsString();

		assertTrue(prometheus.contains("aetherflow_workflows_started_total"),
			"Expected aetherflow_workflows_started_total counter");
		assertTrue(prometheus.contains("aetherflow_workflows_completed_total"),
			"Expected aetherflow_workflows_completed_total counter");
		assertTrue(prometheus.contains("aetherflow_tasks_completed_total"),
			"Expected aetherflow_tasks_completed_total counter");
		assertTrue(prometheus.contains("aetherflow_step_duration_seconds"),
			"Expected aetherflow_step_duration_seconds timer");
	}

	// --- test fixtures ---

	@TestConfiguration
	static class MetricsConfig {

		@Bean
		MetricsTestWorkflow metricsTestWorkflow() {
			return new MetricsTestWorkflow();
		}
	}

	@AIWorkflow("metrics-test-workflow")
	static class MetricsTestWorkflow {

		@Step
		public void stepOne() {}

		@Step(dependsOn = "stepOne")
		public void stepTwo() {}
	}
}
