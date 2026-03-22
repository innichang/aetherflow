package io.github.inni.aetherflow.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "aetherflow")
public record AetherflowProperties(
	@DefaultValue Worker worker,
	@DefaultValue Retry retry,
	@DefaultValue Queue queue,
	@DefaultValue Api api,
	@DefaultValue Triggers triggers
) {

	public record Worker(
		@DefaultValue("250") long pollIntervalMs,
		@DefaultValue("5000") long heartbeatIntervalMs,
		@DefaultValue("30000") long livenessTimeoutMs,
		@DefaultValue("15000") long livenessCheckIntervalMs,
		@DefaultValue("10000") long recoveryIntervalMs
	) {}

	public record Retry(
		@DefaultValue("0") int defaultRetries,
		@DefaultValue("5") long defaultBackoffSeconds
	) {}

	public record Queue(
		@DefaultValue("1") int batchSize,
		@DefaultValue("25") int lockTimeoutSeconds
	) {}

	public record Api(
		@DefaultValue("true") boolean enabled
	) {}

	public record Triggers(List<CronSchedule> cron, List<KafkaTrigger> kafka) {
		public Triggers {
			cron = cron != null ? cron : List.of();
			kafka = kafka != null ? kafka : List.of();
		}
	}

	public record CronSchedule(String workflow, String schedule, String input) {}

	public record KafkaTrigger(String topic, String workflow, String deduplicationKeyField) {}
}
