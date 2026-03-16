package io.github.inni.aetherflow.engine;

public class WorkflowRuntimeException extends RuntimeException {

	public WorkflowRuntimeException(String message) {
		super(message);
	}

	public WorkflowRuntimeException(String message, Throwable cause) {
		super(message, cause);
	}
}

