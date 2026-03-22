package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
	"aetherflow.triggers.cron[0].workflow=cron-test-workflow",
	"aetherflow.triggers.cron[0].schedule=* * * * * *"
})
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, CronTriggerIntegrationTest.CronTestWorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class CronTriggerIntegrationTest {

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Test
	void cronTrigger_createsWorkflowRunsOnSchedule() throws Exception {
		// cron fires every second — wait up to 5s for at least one run to appear
		long deadline = System.currentTimeMillis() + 5_000;
		while (System.currentTimeMillis() < deadline) {
			var runs = workflowRunRepository.findAll().stream()
				.filter(r -> "cron-test-workflow".equals(r.getWorkflowName()))
				.toList();
			if (!runs.isEmpty()) {
				assertFalse(runs.isEmpty());
				return;
			}
			Thread.sleep(200);
		}
		assertFalse(workflowRunRepository.findAll().stream()
			.filter(r -> "cron-test-workflow".equals(r.getWorkflowName()))
			.toList().isEmpty(), "Expected at least one cron-triggered run within 5 seconds");
	}

	@TestConfiguration
	static class CronTestWorkflowConfig {

		@Bean
		CronTestWorkflow cronTestWorkflow() {
			return new CronTestWorkflow();
		}
	}

	@AIWorkflow("cron-test-workflow")
	static class CronTestWorkflow {

		@Step
		public void trigger() {}
	}
}
