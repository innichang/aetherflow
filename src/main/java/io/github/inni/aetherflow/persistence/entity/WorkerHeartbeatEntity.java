package io.github.inni.aetherflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "worker_heartbeats")
public class WorkerHeartbeatEntity {

	@Id
	private UUID id;

	@Column(name = "worker_id", nullable = false)
	private String workerId;

	@Column(name = "recorded_at", nullable = false)
	private OffsetDateTime recordedAt;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getWorkerId() {
		return workerId;
	}

	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}

	public OffsetDateTime getRecordedAt() {
		return recordedAt;
	}

	public void setRecordedAt(OffsetDateTime recordedAt) {
		this.recordedAt = recordedAt;
	}
}
