package io.github.inni.aetherflow.persistence.repository;

import io.github.inni.aetherflow.persistence.entity.StepEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepRepository extends JpaRepository<StepEntity, UUID> {

	List<StepEntity> findByWorkflowIdOrderByStepOrderHintAsc(UUID workflowId);

	Optional<StepEntity> findByWorkflowIdAndStepName(UUID workflowId, String stepName);
}

