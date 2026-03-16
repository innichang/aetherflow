package io.github.inni.aetherflow.examples.order_fulfillment.controller;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.inni.aetherflow.examples.order_fulfillment.service.ExampleOrderFulfillmentService;
import io.github.inni.aetherflow.examples.order_fulfillment.service.ExampleOrderFulfillmentService.ExampleWorkflowRunResponse;
import io.github.inni.aetherflow.examples.order_fulfillment.service.ExampleOrderFulfillmentService.StartExampleWorkflowRequest;
import io.github.inni.aetherflow.examples.order_fulfillment.service.ExampleOrderFulfillmentService.StartExampleWorkflowResponse;

@RestController
@RequestMapping("/example/workflows")
public class ExampleOrderFulfillmentController {

	private final ExampleOrderFulfillmentService exampleWorkflowService;

	public ExampleOrderFulfillmentController(ExampleOrderFulfillmentService exampleWorkflowService) {
		this.exampleWorkflowService = exampleWorkflowService;
	}

	@PostMapping("/order-fulfillment/start")
	public ResponseEntity<StartExampleWorkflowResponse> startOrderFulfillment(
		@RequestBody(required = false) StartExampleWorkflowRequest request
	) {
		StartExampleWorkflowRequest safeRequest = request == null
			? new StartExampleWorkflowRequest(null, null, null)
			: request;
		UUID runId = exampleWorkflowService.startOrderFulfillmentWorkflow(safeRequest);
		StartExampleWorkflowResponse response = new StartExampleWorkflowResponse(
			runId,
			ExampleOrderFulfillmentService.EXAMPLE_WORKFLOW_NAME,
			OffsetDateTime.now()
		);
		return ResponseEntity.accepted().body(response);
	}

	@GetMapping("/runs/{runId}")
	public ResponseEntity<ExampleWorkflowRunResponse> getRun(@PathVariable UUID runId) {
		ExampleWorkflowRunResponse response = exampleWorkflowService.getRun(runId);
		return ResponseEntity.ok(response);
	}
}

