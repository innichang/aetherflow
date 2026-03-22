package io.github.inni.aetherflow.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class KafkaTriggerRouter {

	private static final Logger log = LoggerFactory.getLogger(KafkaTriggerRouter.class);

	private final AetherflowProperties props;
	private final WorkflowEngine workflowEngine;
	private final WorkflowRunRepository workflowRunRepository;
	private final ConcurrentKafkaListenerContainerFactory<String, String> containerFactory;
	private final ObjectMapper objectMapper;
	private final List<MessageListenerContainer> containers = new ArrayList<>();

	public KafkaTriggerRouter(
		AetherflowProperties props,
		WorkflowEngine workflowEngine,
		WorkflowRunRepository workflowRunRepository,
		ConcurrentKafkaListenerContainerFactory<String, String> containerFactory,
		ObjectMapper objectMapper
	) {
		this.props = props;
		this.workflowEngine = workflowEngine;
		this.workflowRunRepository = workflowRunRepository;
		this.containerFactory = containerFactory;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void registerContainers() {
		for (AetherflowProperties.KafkaTrigger trigger : props.triggers().kafka()) {
			log.info("Registering Kafka trigger: topic={} workflow={}", trigger.topic(), trigger.workflow());
			var container = containerFactory.createContainer(trigger.topic());
			container.getContainerProperties().setGroupId("aetherflow-kafka-trigger");
			container.setupMessageListener(
				(org.springframework.kafka.listener.MessageListener<String, String>) record -> handle(trigger, record)
			);
			container.start();
			containers.add(container);
		}
	}

	@PreDestroy
	public void stopContainers() {
		containers.forEach(MessageListenerContainer::stop);
	}

	void handle(AetherflowProperties.KafkaTrigger trigger, ConsumerRecord<String, String> record) {
		String triggerKey = buildTriggerKey(trigger, record);
		if (triggerKey != null && workflowRunRepository.existsByTriggerKey(triggerKey)) {
			log.debug("Skipping duplicate Kafka message: triggerKey={}", triggerKey);
			return;
		}
		try {
			workflowEngine.start(trigger.workflow(), record.value(), triggerKey);
			log.debug("Started workflow '{}' from Kafka topic '{}': triggerKey={}", trigger.workflow(), trigger.topic(), triggerKey);
		} catch (DataIntegrityViolationException e) {
			log.debug("Duplicate Kafka trigger suppressed by constraint: triggerKey={}", triggerKey);
		} catch (Exception e) {
			log.error("Failed to start workflow '{}' from Kafka topic '{}': {}", trigger.workflow(), trigger.topic(), e.getMessage(), e);
		}
	}

	private String buildTriggerKey(AetherflowProperties.KafkaTrigger trigger, ConsumerRecord<String, String> record) {
		if (trigger.deduplicationKeyField() != null && record.value() != null) {
			try {
				JsonNode payload = objectMapper.readTree(record.value());
				JsonNode field = payload.get(trigger.deduplicationKeyField());
				if (field != null) {
					return "kafka:" + trigger.topic() + ":" + field.asText();
				}
			} catch (Exception e) {
				log.warn("Failed to extract dedup field '{}' from Kafka message on topic '{}'",
					trigger.deduplicationKeyField(), trigger.topic());
			}
		}
		return record.key() != null ? "kafka:" + trigger.topic() + ":" + record.key() : null;
	}
}
