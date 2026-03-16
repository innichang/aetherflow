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
@Table(name = "workflow_runs")
public class WorkflowRunEntity extends AuditableEntity {

	@Id
	private UUID id;

	@Column(name = "workflow_id", nullable = false)
	private UUID workflowId;

	@Column(name = "workflow_name", nullable = false)
	private String workflowName;

	@Column(name = "workflow_version", nullable = false)
	private int workflowVersion;

	@Column(name = "status", nullable = false)
	private String status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "input_payload", columnDefinition = "jsonb")
	private String inputPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "output_payload", columnDefinition = "jsonb")
	private String outputPayload;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "started_at", nullable = false)
	private OffsetDateTime startedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getWorkflowId() {
		return workflowId;
	}

	public void setWorkflowId(UUID workflowId) {
		this.workflowId = workflowId;
	}

	public String getWorkflowName() {
		return workflowName;
	}

	public void setWorkflowName(String workflowName) {
		this.workflowName = workflowName;
	}

	public int getWorkflowVersion() {
		return workflowVersion;
	}

	public void setWorkflowVersion(int workflowVersion) {
		this.workflowVersion = workflowVersion;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
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
}

