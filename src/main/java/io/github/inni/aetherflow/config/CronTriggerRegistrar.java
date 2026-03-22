package io.github.inni.aetherflow.config;

import io.github.inni.aetherflow.engine.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class CronTriggerRegistrar implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(CronTriggerRegistrar.class);

	private final AetherflowProperties props;
	private final WorkflowEngine workflowEngine;

	public CronTriggerRegistrar(AetherflowProperties props, WorkflowEngine workflowEngine) {
		this.props = props;
		this.workflowEngine = workflowEngine;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		for (AetherflowProperties.CronSchedule def : props.triggers().cron()) {
			log.info("Registering cron trigger: workflow={} schedule={}", def.workflow(), def.schedule());
			registrar.addCronTask(
				() -> {
					try {
						workflowEngine.start(def.workflow(), def.input());
					} catch (Exception e) {
						log.error("Cron trigger failed for workflow '{}': {}", def.workflow(), e.getMessage(), e);
					}
				},
				def.schedule()
			);
		}
	}
}
