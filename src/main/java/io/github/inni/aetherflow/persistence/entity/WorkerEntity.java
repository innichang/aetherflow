package io.github.inni.aetherflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "workers")
public class WorkerEntity {

	@Id
	private String id;

	@Column(name = "hostname")
	private String hostname;

	@Column(name = "started_at", nullable = false)
	private OffsetDateTime startedAt;

	@Column(name = "last_heartbeat", nullable = false)
	private OffsetDateTime lastHeartbeat;

	@Column(name = "status", nullable = false)
	private String status;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public OffsetDateTime getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(OffsetDateTime startedAt) {
		this.startedAt = startedAt;
	}

	public OffsetDateTime getLastHeartbeat() {
		return lastHeartbeat;
	}

	public void setLastHeartbeat(OffsetDateTime lastHeartbeat) {
		this.lastHeartbeat = lastHeartbeat;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
