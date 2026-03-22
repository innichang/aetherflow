package io.github.inni.aetherflow.examples.phase_3_news_ingestion_pipeline.workflow;

import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Phase 3 example: News Article Ingestion Pipeline.
 *
 * <p>Demonstrates the full Phase 1–3 feature set:
 *
 * <ul>
 *   <li>DAG with two parallel branches (generateSummary ∥ generateEmbeddings)
 *   <li>Retries with exponential backoff on steps that call external AI APIs
 *   <li>Step timeout on the search indexing step
 *   <li>Triggered automatically via Kafka (real-time) and cron (scheduled catch-up)
 *   <li>Deduplication: the Kafka trigger uses {@code articleId} as the dedup key so
 *       re-published events for the same article never start a duplicate run
 *   <li>Run status inspectable via the built-in REST API (no custom controller needed)
 * </ul>
 *
 * <p>DAG shape:
 * <pre>
 *   fetchArticle
 *       │
 *   validateContent
 *       ├──────────────────┐
 *   generateSummary   generateEmbeddings
 *       │                  │
 *   storeSummary      indexToSearch
 *       └──────────────────┘
 *              │
 *       notifySubscribers
 * </pre>
 *
 * <p>How to trigger:
 * <ul>
 *   <li>Kafka: publish a JSON message to {@code articles.submitted} — the engine picks up
 *       {@code articleId} from the payload as the dedup key (see application.yaml excerpt
 *       in {@code NewsIngestionTriggerGuide}).
 *   <li>Cron: runs every day at midnight to re-process any articles that were missed.
 *   <li>REST: {@code POST /workflows/start} with body {@code {"workflowName":"news-ingestion","input":{...}}}.
 * </ul>
 *
 * <p>How to inspect progress:
 * <pre>
 *   GET /workflow-runs/{runId}
 *   GET /workflow-runs?workflowName=news-ingestion&status=running
 * </pre>
 */
@Component
@AIWorkflow("news-ingestion")
public class NewsIngestionWorkflow {

	// -------------------------------------------------------------------------
	// Root step — no dependencies, runs first
	// -------------------------------------------------------------------------

	@Step
	public void fetchArticle() {
		log("Fetching article from source URL");
		sleep(60);
	}

	// -------------------------------------------------------------------------
	// Validation — runs after fetch
	// -------------------------------------------------------------------------

	@Step(dependsOn = "fetchArticle")
	public void validateContent() {
		log("Validating content (not empty, not spam)");
		sleep(30);
	}

	// -------------------------------------------------------------------------
	// Parallel AI branch 1 — calls a language model; retries on transient errors
	// retries=3, backoff="5s" → waits 5s, 10s, 20s between attempts
	// -------------------------------------------------------------------------

	@Step(dependsOn = "validateContent", retries = 3, backoff = "5s")
	public void generateSummary() {
		log("Calling LLM to generate article summary (may be flaky — retries enabled)");
		sleep(120);
	}

	// -------------------------------------------------------------------------
	// Parallel AI branch 2 — calls an embedding model; also retries independently
	// Both branches run at the same time once validateContent completes
	// -------------------------------------------------------------------------

	@Step(dependsOn = "validateContent", retries = 3, backoff = "5s")
	public void generateEmbeddings() {
		log("Calling embedding model to vectorise article content");
		sleep(150);
	}

	// -------------------------------------------------------------------------
	// Downstream of branch 1
	// -------------------------------------------------------------------------

	@Step(dependsOn = "generateSummary")
	public void storeSummary() {
		log("Persisting generated summary to the articles table");
		sleep(20);
	}

	// -------------------------------------------------------------------------
	// Downstream of branch 2 — timeoutSeconds guards against a stalled index writer
	// -------------------------------------------------------------------------

	@Step(dependsOn = "generateEmbeddings", timeoutSeconds = 30)
	public void indexToSearch() {
		log("Indexing article vectors into the search engine");
		sleep(80);
	}

	// -------------------------------------------------------------------------
	// Fan-in — waits for BOTH branches to finish before notifying
	// -------------------------------------------------------------------------

	@Step(dependsOn = {"storeSummary", "indexToSearch"})
	public void notifySubscribers() {
		log("Sending push notifications / email digest to subscribers");
		sleep(40);
	}

	// -------------------------------------------------------------------------

	private void log(String message) {
		System.out.printf("[news-ingestion] %s%n", message);
	}

	private void sleep(long millis) {
		try {
			TimeUnit.MILLISECONDS.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
