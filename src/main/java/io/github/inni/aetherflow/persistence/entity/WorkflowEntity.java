package io.github.inni.aetherflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "workflows")
public class WorkflowEntity extends AuditableEntity {

	@Id
	private UUID id;

	@Column(name = "workflow_name", nullable = false, unique = true)
	private String workflowName;

	@Column(name = "workflow_version", nullable = false)
	private int workflowVersion;

	@Column(name = "description")
	private String description;

	@Column(name = "bean_name", nullable = false)
	private String beanName;

	@Column(name = "class_name", nullable = false)
	private String className;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getBeanName() {
		return beanName;
	}

	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}
}

