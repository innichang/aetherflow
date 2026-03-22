package io.github.inni.aetherflow.engine;

public final class ExecutionStatus {

	public static final String PENDING = "pending";
	public static final String RUNNING = "running";
	public static final String COMPLETED = "completed";
	public static final String FAILED = "failed";
	public static final String RETRYING = "retrying";
	public static final String TIMED_OUT = "timed_out";

	private ExecutionStatus() {
	}
}

