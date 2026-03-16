package io.github.inni.aetherflow.examples.order_fulfillment.service;

import io.github.inni.aetherflow.engine.WorkflowEngine;
import io.github.inni.aetherflow.persistence.entity.WorkflowRunEntity;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.WorkflowRunRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExampleOrderFulfillmentService {

	public static final String EXAMPLE_WORKFLOW_NAME = "order-fulfillment-workflow";

	private final WorkflowEngine workflowEngine;
	private final WorkflowRunRepository workflowRunRepository;
	private final StepRunRepository stepRunRepository;

	public ExampleOrderFulfillmentService(
		WorkflowEngine workflowEngine,
		WorkflowRunRepository workflowRunRepository,
		StepRunRepository stepRunRepository
	) {
		this.workflowEngine = workflowEngine;
		this.workflowRunRepository = workflowRunRepository;
		this.stepRunRepository = stepRunRepository;
	}

	public UUID startOrderFulfillmentWorkflow(StartExampleWorkflowRequest request) {
		String payload = """
			{"orderId":"%s","customerId":"%s","notes":"%s"}
			""".formatted(
			nullSafe(request.orderId()),
			nullSafe(request.customerId()),
			nullSafe(request.notes())
		).replace('\n', ' ').trim();
		return workflowEngine.start(EXAMPLE_WORKFLOW_NAME, payload);
	}

	public ExampleWorkflowRunResponse getRun(UUID runId) {
		WorkflowRunEntity run = workflowRunRepository.findById(runId)
			.orElseThrow(() -> new IllegalArgumentException("Workflow run '%s' not found".formatted(runId)));
		List<StepRunSummary> steps = stepRunRepository.findByWorkflowRunId(runId)
			.stream()
			.map(stepRun -> new StepRunSummary(
				stepRun.getStepName(),
				stepRun.getStatus(),
				stepRun.getAttempt(),
				stepRun.getStartedAt(),
				stepRun.getCompletedAt(),
				stepRun.getErrorMessage()
			))
			.toList();

		return new ExampleWorkflowRunResponse(
			run.getId(),
			run.getWorkflowName(),
			run.getStatus(),
			run.getStartedAt(),
			run.getCompletedAt(),
			run.getErrorMessage(),
			steps
		);
	}

	private String nullSafe(String value) {
		return value == null ? "" : value;
	}

	public record StartExampleWorkflowRequest(String orderId, String customerId, String notes) {
	}

	public record StartExampleWorkflowResponse(UUID runId, String workflowName, OffsetDateTime startedAt) {
	}

	public record ExampleWorkflowRunResponse(
		UUID runId,
		String workflowName,
		String status,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt,
		String errorMessage,
		List<StepRunSummary> steps
	) {
	}

	public record StepRunSummary(
		String stepName,
		String status,
		int attempt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt,
		String errorMessage
	) {
	}
}

