package io.github.inni.aetherflow.persistence.repository;

import io.github.inni.aetherflow.persistence.entity.WorkerEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<WorkerEntity, String> {

	List<WorkerEntity> findByStatusAndLastHeartbeatBefore(String status, OffsetDateTime threshold);

	long countByStatus(String status);
}
