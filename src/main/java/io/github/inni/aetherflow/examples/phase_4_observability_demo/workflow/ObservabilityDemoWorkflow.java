package io.github.inni.aetherflow.examples.phase_4_observability_demo.workflow;

import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Phase 4 example: Observability Demo Workflow.
 *
 * <p>Designed to produce interesting signal across all Phase 4 observability features:
 *
 * <ul>
 *   <li>Structured execution logs at every lifecycle event (start, complete, retry, fail)
 *   <li>Prometheus metrics: step latency, retry counter, workflow completion counter
 *   <li>Dashboard: live DAG progress viewable at {@code GET /dashboard/runs/{runId}}
 *   <li>Grafana: retry rate and failure counters visible in the Reliability row
 * </ul>
 *
 * <p>DAG shape:
 * <pre>
 *   prepareData
 *       │
 *   processData  ← fails on first attempt, succeeds on retry
 *       │
 *   reportResults
 * </pre>
 *
 * <p>How to trigger:
 * <pre>
 * POST /workflows/start
 * {"workflowName": "observability-demo"}
 * </pre>
 *
 * <p>What to observe while it runs:
 * <ol>
 *   <li>Logs: look for {@code event=step.retrying} on {@code processData} — you will see
 *       {@code attempt=1}, {@code retries_remaining=1}, {@code error_type=RuntimeException},
 *       and the correlated {@code workflow_run_id} / {@code step_run_id} in every line.
 *   <li>Dashboard: open {@code GET /dashboard/runs/{runId}} and watch the DAG nodes change
 *       colour in real time — processData turns orange (retrying) before turning green.
 *   <li>Prometheus: {@code GET /actuator/prometheus} — after a run completes you will find
 *       non-zero values for {@code aetherflow_tasks_retried_total} and
 *       {@code aetherflow_step_duration_seconds}.
 *   <li>Grafana: import {@code docs/grafana/aetherflow-dashboard.json} — the Retry Rate
 *       panel will show a spike, and the Workflow Completion counter will increment.
 * </ol>
 */
@Component
@AIWorkflow("observability-demo")
public class ObservabilityDemoWorkflow {

	// Tracks attempts across calls on the same JVM instance.
	// Resets to 0 each time the Spring context restarts.
	private final AtomicInteger processAttempts = new AtomicInteger(0);

	@Step
	public void prepareData() {
		log("Preparing input data");
		sleep(50);
	}

	/**
	 * Fails on the first attempt to demonstrate retry mechanics and the
	 * {@code step.retrying} log event. Succeeds on the second attempt.
	 */
	@Step(dependsOn = "prepareData", retries = 2, backoff = "2s")
	public void processData() {
		int attempt = processAttempts.incrementAndGet();
		log("processData attempt #" + attempt);
		if (attempt == 1) {
			throw new RuntimeException("Simulated transient failure on first attempt");
		}
		sleep(80);
		log("processData succeeded on attempt #" + attempt);
	}

	@Step(dependsOn = "processData")
	public void reportResults() {
		log("Generating and storing results report");
		sleep(40);
	}

	private void log(String message) {
		System.out.printf("[observability-demo] %s%n", message);
	}

	private void sleep(long millis) {
		try {
			TimeUnit.MILLISECONDS.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
