package io.github.inni.aetherflow.api;

import io.github.inni.aetherflow.api.WorkflowDefinitionQueryService.WorkflowDefinitionResponse;
import io.github.inni.aetherflow.api.WorkflowDefinitionQueryService.WorkflowSummary;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "aetherflow.api.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowDefinitionController {

	private final WorkflowDefinitionQueryService queryService;

	public WorkflowDefinitionController(WorkflowDefinitionQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping("/workflows")
	public ResponseEntity<List<WorkflowSummary>> listWorkflows() {
		return ResponseEntity.ok(queryService.listWorkflows());
	}

	@GetMapping("/workflows/{name}/definition")
	public ResponseEntity<?> getDefinition(@PathVariable String name) {
		try {
			WorkflowDefinitionResponse definition = queryService.getDefinition(name);
			return ResponseEntity.ok(definition);
		} catch (NoSuchElementException e) {
			return ResponseEntity.notFound().build();
		}
	}
}
