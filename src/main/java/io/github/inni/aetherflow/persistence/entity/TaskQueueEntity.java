package io.github.inni.aetherflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_queue")
public class TaskQueueEntity extends AuditableEntity {

	@Id
	private UUID id;

	@Column(name = "workflow_run_id", nullable = false)
	private UUID workflowRunId;

	@Column(name = "step_run_id", nullable = false)
	private UUID stepRunId;

	@Column(name = "workflow_name", nullable = false)
	private String workflowName;

	@Column(name = "step_name", nullable = false)
	private String stepName;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "priority", nullable = false)
	private int priority;

	@Column(name = "available_at", nullable = false)
	private OffsetDateTime availableAt;

	@Column(name = "locked_at")
	private OffsetDateTime lockedAt;

	@Column(name = "lock_expires_at")
	private OffsetDateTime lockExpiresAt;

	@Column(name = "worker_id")
	private String workerId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "queue_message", columnDefinition = "jsonb")
	private String queueMessage;

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

	public UUID getStepRunId() {
		return stepRunId;
	}

	public void setStepRunId(UUID stepRunId) {
		this.stepRunId = stepRunId;
	}

	public String getWorkflowName() {
		return workflowName;
	}

	public void setWorkflowName(String workflowName) {
		this.workflowName = workflowName;
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

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public OffsetDateTime getAvailableAt() {
		return availableAt;
	}

	public void setAvailableAt(OffsetDateTime availableAt) {
		this.availableAt = availableAt;
	}

	public OffsetDateTime getLockedAt() {
		return lockedAt;
	}

	public void setLockedAt(OffsetDateTime lockedAt) {
		this.lockedAt = lockedAt;
	}

	public OffsetDateTime getLockExpiresAt() {
		return lockExpiresAt;
	}

	public void setLockExpiresAt(OffsetDateTime lockExpiresAt) {
		this.lockExpiresAt = lockExpiresAt;
	}

	public String getWorkerId() {
		return workerId;
	}

	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}

	public String getQueueMessage() {
		return queueMessage;
	}

	public void setQueueMessage(String queueMessage) {
		this.queueMessage = queueMessage;
	}
}

