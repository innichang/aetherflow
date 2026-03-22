package io.github.inni.aetherflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.inni.aetherflow.TestcontainersConfiguration;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
	"aetherflow.triggers.kafka[0].topic=kafka-trigger-test-topic",
	"aetherflow.triggers.kafka[0].workflow=kafka-trigger-test-workflow",
	"aetherflow.triggers.kafka[0].deduplication-key-field=orderId"
})
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, KafkaTriggerIntegrationTest.KafkaTestWorkflowConfig.class})
@EnabledIf("io.github.inni.aetherflow.support.DockerAvailability#isDockerAvailable")
class KafkaTriggerIntegrationTest {

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private WorkflowRunRepository workflowRunRepository;

	@Test
	void kafkaMessage_startsWorkflowRun() throws Exception {
		kafkaTemplate.send("kafka-trigger-test-topic", """
			{"orderId":"order-001","item":"widget"}
			""".strip()).get(5, TimeUnit.SECONDS);

		long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			long count = workflowRunRepository.findAll().stream()
				.filter(r -> "kafka-trigger-test-workflow".equals(r.getWorkflowName()))
				.count();
			if (count >= 1) {
				assertEquals(1, count);
				return;
			}
			Thread.sleep(200);
		}
		assertEquals(1, workflowRunRepository.findAll().stream()
			.filter(r -> "kafka-trigger-test-workflow".equals(r.getWorkflowName()))
			.count(), "Expected workflow run to be created within 10 seconds");
	}

	@Test
	void duplicateKafkaMessage_startsOnlyOneRun() throws Exception {
		String message = """
			{"orderId":"order-dedup-001","item":"widget"}
			""".strip();

		kafkaTemplate.send("kafka-trigger-test-topic", message).get(5, TimeUnit.SECONDS);
		kafkaTemplate.send("kafka-trigger-test-topic", message).get(5, TimeUnit.SECONDS);

		// wait for messages to be consumed
		Thread.sleep(3_000);

		long count = workflowRunRepository.findAll().stream()
			.filter(r -> "kafka-trigger-test-workflow".equals(r.getWorkflowName())
				&& r.getTriggerKey() != null && r.getTriggerKey().contains("order-dedup-001"))
			.count();
		assertEquals(1, count, "Duplicate Kafka message should produce exactly one workflow run");
	}

	@TestConfiguration
	static class KafkaTestWorkflowConfig {

		@Bean
		KafkaTriggerTestWorkflow kafkaTriggerTestWorkflow() {
			return new KafkaTriggerTestWorkflow();
		}
	}

	@AIWorkflow("kafka-trigger-test-workflow")
	static class KafkaTriggerTestWorkflow {

		@Step
		public void process() {}
	}
}
