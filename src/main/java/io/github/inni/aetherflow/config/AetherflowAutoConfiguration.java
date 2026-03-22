package io.github.inni.aetherflow.config;

import io.github.inni.aetherflow.engine.WorkflowEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration entry point for the Aetherflow workflow engine.
 *
 * <p>This class is registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so that Spring Boot discovers and applies it automatically when the engine is on the classpath.
 *
 * <p>Conditional features:
 * <ul>
 *   <li>REST API ({@code POST /workflows/start}, {@code GET /workflow-runs/**}) — controlled by
 *       {@code aetherflow.api.enabled} (default {@code true}).
 *   <li>Cron triggers — active only when {@code aetherflow.triggers.cron} entries are present.
 *   <li>Kafka triggers — active only when {@code aetherflow.triggers.kafka} entries are present.
 * </ul>
 *
 * <p>Multi-module note: in the current layout all engine beans are picked up by the application's
 * component scan. When this engine is extracted into a standalone
 * {@code aetherflow-spring-boot-starter} JAR, the {@code @Component} / {@code @Service}
 * annotations on the engine classes would be replaced by explicit {@code @Bean} methods here,
 * allowing consumers to override individual beans via {@code @ConditionalOnMissingBean}.
 */
@AutoConfiguration
@ConditionalOnClass(WorkflowEngine.class)
@EnableScheduling
@EnableConfigurationProperties(AetherflowProperties.class)
public class AetherflowAutoConfiguration {
}
