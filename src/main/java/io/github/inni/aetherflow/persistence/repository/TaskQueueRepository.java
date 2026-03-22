package io.github.inni.aetherflow.persistence.repository;

import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskQueueRepository extends JpaRepository<TaskQueueEntity, UUID> {

	List<TaskQueueEntity> findByWorkflowRunId(UUID workflowRunId);

	long countByStatus(String status);
}

