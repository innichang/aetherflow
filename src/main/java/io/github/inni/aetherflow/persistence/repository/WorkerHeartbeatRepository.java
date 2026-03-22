package io.github.inni.aetherflow.persistence.repository;

import io.github.inni.aetherflow.persistence.entity.WorkerHeartbeatEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeatEntity, UUID> {
}
