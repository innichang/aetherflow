package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, WorkflowDefinitionApiIntegrationTest.DefApiConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class WorkflowDefinitionApiIntegrationTest {

	@Autowired
	private WebApplicationContext wac;

	@Autowired
	private ObjectMapper objectMapper;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
	}

	@Test
	void listWorkflows_includesRegisteredWorkflow() throws Exception {
		MvcResult result = mockMvc.perform(get("/workflows"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		boolean found = false;
		for (JsonNode node : body) {
			if ("def-api-workflow".equals(node.get("workflowName").asText())) {
				assertEquals(3, node.get("stepCount").asInt());
				found = true;
				break;
			}
		}
		assert found : "def-api-workflow not found in /workflows response";
	}

	@Test
	void getDefinition_returnsStepsInTopologicalOrderWithDependencies() throws Exception {
		MvcResult result = mockMvc.perform(get("/workflows/def-api-workflow/definition"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workflowName").value("def-api-workflow"))
			.andExpect(jsonPath("$.steps").isArray())
			.andExpect(jsonPath("$.steps.length()").value(3))
			.andReturn();

		JsonNode steps = objectMapper.readTree(result.getResponse().getContentAsString()).get("steps");

		// Root step — no dependencies
		JsonNode extract = steps.get(0);
		assertEquals("extract", extract.get("name").asText());
		assertEquals(0, extract.get("dependsOn").size());

		// Middle step depends on root
		JsonNode transform = steps.get(1);
		assertEquals("transform", transform.get("name").asText());
		assertEquals(List.of("extract"), objectMapper.convertValue(transform.get("dependsOn"), List.class));

		// Leaf step depends on middle
		JsonNode load = steps.get(2);
		assertEquals("load", load.get("name").asText());
		assertEquals(List.of("transform"), objectMapper.convertValue(load.get("dependsOn"), List.class));
	}

	@Test
	void getDefinition_unknownWorkflow_returns404() throws Exception {
		mockMvc.perform(get("/workflows/no-such-workflow/definition"))
			.andExpect(status().isNotFound());
	}

	// --- test fixtures ---

	@TestConfiguration
	static class DefApiConfig {

		@Bean
		DefApiWorkflow defApiWorkflow() {
			return new DefApiWorkflow();
		}
	}

	@AIWorkflow("def-api-workflow")
	static class DefApiWorkflow {

		@Step(name = "extract")
		public void extract() {}

		@Step(name = "transform", dependsOn = "extract")
		public void transform() {}

		@Step(name = "load", dependsOn = "transform")
		public void load() {}
	}
}
