package io.github.inni.aetherflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.persistence.entity.StepRunEntity;
import io.github.inni.aetherflow.persistence.entity.WorkflowRunEntity;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "aetherflow.api.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowRunQueryService {

	private final WorkflowEngine workflowEngine;
	private final WorkflowRunRepository workflowRunRepository;
	private final StepRunRepository stepRunRepository;

	public WorkflowRunQueryService(
		WorkflowEngine workflowEngine,
		WorkflowRunRepository workflowRunRepository,
		StepRunRepository stepRunRepository
	) {
		this.workflowEngine = workflowEngine;
		this.workflowRunRepository = workflowRunRepository;
		this.stepRunRepository = stepRunRepository;
	}

	public StartWorkflowResponse startWorkflow(StartWorkflowRequest request) {
		String inputPayload = request.input() != null ? request.input().toString() : null;
		UUID runId = workflowEngine.start(request.workflowName(), inputPayload);
		return new StartWorkflowResponse(runId, request.workflowName(), OffsetDateTime.now());
	}

	public WorkflowRunPage listRuns(String status, String workflowName, OffsetDateTime from, OffsetDateTime to, int page, int size) {
		Specification<WorkflowRunEntity> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (status != null) predicates.add(cb.equal(root.get("status"), status));
			if (workflowName != null) predicates.add(cb.equal(root.get("workflowName"), workflowName));
			if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from));
			if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), to));
			return cb.and(predicates.toArray(new Predicate[0]));
		};

		Page<WorkflowRunEntity> result = workflowRunRepository.findAll(
			spec,
			PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
		);

		List<WorkflowRunSummary> content = result.getContent().stream()
			.map(run -> new WorkflowRunSummary(run.getId(), run.getWorkflowName(), run.getStatus(), run.getStartedAt(), run.getCompletedAt()))
			.toList();

		return new WorkflowRunPage(content, result.getNumber(), result.getSize(), result.getTotalElements());
	}

	public WorkflowRunDetail getRun(UUID runId) {
		WorkflowRunEntity run = workflowRunRepository.findById(runId)
			.orElseThrow(() -> new NoSuchElementException("Workflow run '%s' not found".formatted(runId)));

		List<StepRunSummary> steps = stepRunRepository.findByWorkflowRunId(runId).stream()
			.map(s -> new StepRunSummary(
				s.getStepName(), s.getStatus(), s.getAttempt(), s.getRetryCount(),
				s.getStartedAt(), s.getCompletedAt(), s.getDurationMs(), s.getErrorMessage()
			))
			.toList();

		return new WorkflowRunDetail(
			run.getId(), run.getWorkflowName(), run.getStatus(), run.getInputPayload(),
			run.getStartedAt(), run.getCompletedAt(), run.getErrorMessage(), steps
		);
	}

	// --- request/response records ---

	public record StartWorkflowRequest(String workflowName, JsonNode input) {}

	public record StartWorkflowResponse(UUID runId, String workflowName, OffsetDateTime startedAt) {}

	public record WorkflowRunPage(List<WorkflowRunSummary> content, int page, int size, long totalElements) {}

	public record WorkflowRunSummary(
		UUID runId,
		String workflowName,
		String status,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt
	) {}

	public record WorkflowRunDetail(
		UUID runId,
		String workflowName,
		String status,
		String inputPayload,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt,
		String errorMessage,
		List<StepRunSummary> steps
	) {}

	public record StepRunSummary(
		String stepName,
		String status,
		int attempt,
		int retryCount,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt,
		Long durationMs,
		String errorMessage
	) {}
}
