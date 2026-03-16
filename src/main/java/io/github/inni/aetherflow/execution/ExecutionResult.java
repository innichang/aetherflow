package io.github.inni.aetherflow.execution;

public record ExecutionResult(boolean success, long durationMs, Throwable error) {

	public static ExecutionResult success(long durationMs) {
		return new ExecutionResult(true, durationMs, null);
	}

	public static ExecutionResult failure(long durationMs, Throwable error) {
		return new ExecutionResult(false, durationMs, error);
	}
}

