package io.github.inni.aetherflow.persistence.repository;

import io.github.inni.aetherflow.persistence.entity.StepRunEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepRunRepository extends JpaRepository<StepRunEntity, UUID> {

	List<StepRunEntity> findByWorkflowRunId(UUID workflowRunId);

	List<StepRunEntity> findByWorkflowRunIdAndStatus(UUID workflowRunId, String status);

	Optional<StepRunEntity> findByWorkflowRunIdAndStepNameAndAttempt(UUID workflowRunId, String stepName, int attempt);
}

