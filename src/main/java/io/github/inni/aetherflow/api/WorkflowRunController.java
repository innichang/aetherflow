package io.github.inni.aetherflow.api;

import io.github.inni.aetherflow.api.WorkflowRunQueryService.StartWorkflowRequest;
import io.github.inni.aetherflow.api.WorkflowRunQueryService.StartWorkflowResponse;
import io.github.inni.aetherflow.api.WorkflowRunQueryService.WorkflowRunDetail;
import io.github.inni.aetherflow.api.WorkflowRunQueryService.WorkflowRunPage;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "aetherflow.api.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowRunController {

	private final WorkflowRunQueryService queryService;

	public WorkflowRunController(WorkflowRunQueryService queryService) {
		this.queryService = queryService;
	}

	@PostMapping("/workflows/start")
	public ResponseEntity<?> startWorkflow(@RequestBody StartWorkflowRequest request) {
		try {
			StartWorkflowResponse response = queryService.startWorkflow(request);
			return ResponseEntity.accepted().body(response);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@GetMapping("/workflow-runs")
	public ResponseEntity<WorkflowRunPage> listRuns(
		@RequestParam(required = false) String status,
		@RequestParam(required = false) String workflowName,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		return ResponseEntity.ok(queryService.listRuns(status, workflowName, from, to, page, size));
	}

	@GetMapping("/workflow-runs/{id}")
	public ResponseEntity<?> getRun(@PathVariable UUID id) {
		try {
			WorkflowRunDetail detail = queryService.getRun(id);
			return ResponseEntity.ok(detail);
		} catch (NoSuchElementException e) {
			return ResponseEntity.notFound().build();
		}
	}
}
