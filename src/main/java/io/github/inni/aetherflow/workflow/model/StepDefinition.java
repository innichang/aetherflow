package io.github.inni.aetherflow.workflow.model;

import java.util.List;

public record StepDefinition(
	String stepName,
	String methodName,
	List<String> dependsOn,
	int retries,
	int backoffSeconds,
	Integer timeoutSeconds,
	Integer concurrencyLimit
) {
}

