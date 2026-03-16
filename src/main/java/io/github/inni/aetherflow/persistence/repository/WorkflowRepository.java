package io.github.inni.aetherflow.persistence.repository;

import io.github.inni.aetherflow.persistence.entity.WorkflowEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {

	Optional<WorkflowEntity> findByWorkflowName(String workflowName);
}

