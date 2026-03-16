package io.github.inni.aetherflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "step_runs")
public class StepRunEntity extends AuditableEntity {

	@Id
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false)
	private UUID workflowRunId;

	@Column(name = "workflow_id", nullable = false)
	private UUID workflowId;

	@Column(name = "step_id", nullable = false)
	private UUID stepId;

	@Column(name = "step_name", nullable = false)
	private String stepName;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "attempt", nullable = false)
	private int attempt;

	@Column(name = "max_retries", nullable = false)
	private int maxRetries;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "next_retry_time")
	private OffsetDateTime nextRetryTime;

	@Column(name = "idempotency_key")
	private String idempotencyKey;

	@Column(name = "worker_id")
	private String workerId;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "input_payload", columnDefinition = "jsonb")
	private String inputPayload;

	@Column(name = "output_payload", columnDefinition = "jsonb")
	private String outputPayload;

	@Column(name = "error_type")
	private String errorType;

	@Column(name = "error_message")
	private String errorMessage;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getWorkflowRunId() {
		return workflowRunId;
	}

	public void setWorkflowRunId(UUID workflowRunId) {
		this.workflowRunId = workflowRunId;
	}

	public UUID getWorkflowId() {
		return workflowId;
	}

	public void setWorkflowId(UUID workflowId) {
		this.workflowId = workflowId;
	}

	public UUID getStepId() {
		return stepId;
	}

	public void setStepId(UUID stepId) {
		this.stepId = stepId;
	}

	public String getStepName() {
		return stepName;
	}

	public void setStepName(String stepName) {
		this.stepName = stepName;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getAttempt() {
		return attempt;
	}

	public void setAttempt(int attempt) {
		this.attempt = attempt;
	}

	public int getMaxRetries() {
		return maxRetries;
	}

	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public OffsetDateTime getNextRetryTime() {
		return nextRetryTime;
	}

	public void setNextRetryTime(OffsetDateTime nextRetryTime) {
		this.nextRetryTime = nextRetryTime;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getWorkerId() {
		return workerId;
	}

	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}

	public OffsetDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(OffsetDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(OffsetDateTime completedAt) {
		this.completedAt = completedAt;
	}

	public Long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(Long durationMs) {
		this.durationMs = durationMs;
	}

	public String getInputPayload() {
		return inputPayload;
	}

	public void setInputPayload(String inputPayload) {
		this.inputPayload = inputPayload;
	}

	public String getOutputPayload() {
		return outputPayload;
	}

	public void setOutputPayload(String outputPayload) {
		this.outputPayload = outputPayload;
	}

	public String getErrorType() {
		return errorType;
	}

	public void setErrorType(String errorType) {
		this.errorType = errorType;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}

