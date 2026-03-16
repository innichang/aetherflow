package io.github.inni.aetherflow.persistence.repository;

import io.github.inni.aetherflow.persistence.entity.WorkflowRunEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, UUID> {
}

