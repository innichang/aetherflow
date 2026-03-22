package io.github.inni.aetherflow.persistence.repository;

import io.github.inni.aetherflow.persistence.entity.WorkflowRunEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, UUID>, JpaSpecificationExecutor<WorkflowRunEntity> {

	boolean existsByTriggerKey(String triggerKey);
}

