package io.github.inni.aetherflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "steps")
public class StepEntity extends AuditableEntity {

	@Id
	private UUID id;

	@Column(name = "workflow_id", nullable = false)
	private UUID workflowId;

	@Column(name = "step_name", nullable = false)
	private String stepName;

	@Column(name = "method_name", nullable = false)
	private String methodName;

	@Column(name = "step_order_hint")
	private Integer stepOrderHint;

	@Column(name = "depends_on", nullable = false)
	private String dependsOn;

	@Column(name = "retries", nullable = false)
	private int retries;

	@Column(name = "timeout_seconds")
	private Integer timeoutSeconds;

	@Column(name = "concurrency_limit")
	private Integer concurrencyLimit;

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

	public String getStepName() {
		return stepName;
	}

	public void setStepName(String stepName) {
		this.stepName = stepName;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public Integer getStepOrderHint() {
		return stepOrderHint;
	}

	public void setStepOrderHint(Integer stepOrderHint) {
		this.stepOrderHint = stepOrderHint;
	}

	public String getDependsOn() {
		return dependsOn;
	}

	public void setDependsOn(String dependsOn) {
		this.dependsOn = dependsOn;
	}

	public int getRetries() {
		return retries;
	}

	public void setRetries(int retries) {
		this.retries = retries;
	}

	public Integer getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(Integer timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public Integer getConcurrencyLimit() {
		return concurrencyLimit;
	}

	public void setConcurrencyLimit(Integer concurrencyLimit) {
		this.concurrencyLimit = concurrencyLimit;
	}
}

