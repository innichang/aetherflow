package io.github.inni.aetherflow.examples.phase_3_news_ingestion_pipeline.api;

/**
 * Guide: using the Phase 3 built-in REST API with the news-ingestion workflow.
 *
 * <p>Phase 3 ships {@code WorkflowRunController} and {@code WorkflowRunQueryService}
 * automatically when {@code aetherflow.api.enabled=true} (the default). No custom
 * controller is needed — the three endpoints below cover the full lifecycle.
 *
 * <p>All examples assume the server is running on {@code localhost:8080}.
 *
 * <hr>
 *
 * <h2>1. Start a run manually</h2>
 * <pre>{@code
 * POST /workflows/start
 * Content-Type: application/json
 *
 * {
 *   "workflowName": "news-ingestion",
 *   "input": {
 *     "articleId": "art-2026-03-22-001",
 *     "sourceUrl": "https://example.com/articles/spring-ai-pipelines",
 *     "publishedAt": "2026-03-22T09:00:00Z"
 *   }
 * }
 *
 * HTTP 202 Accepted
 * {
 *   "runId": "a3f1c2d4-5678-...",
 *   "workflowName": "news-ingestion",
 *   "startedAt": "2026-03-22T10:00:00Z"
 * }
 * }</pre>
 *
 * <hr>
 *
 * <h2>2. Inspect step-by-step progress</h2>
 *
 * <p>Poll this endpoint to watch the DAG execute. The two AI branches
 * ({@code generateSummary} and {@code generateEmbeddings}) appear as concurrent
 * {@code running} entries while the fan-in step ({@code notifySubscribers}) remains
 * {@code pending}.
 *
 * <pre>{@code
 * GET /workflow-runs/a3f1c2d4-5678-...
 *
 * HTTP 200 OK
 * {
 *   "runId": "a3f1c2d4-5678-...",
 *   "workflowName": "news-ingestion",
 *   "status": "running",
 *   "startedAt": "2026-03-22T10:00:00Z",
 *   "completedAt": null,
 *   "steps": [
 *     { "stepName": "fetchArticle",        "status": "completed", "durationMs": 62  },
 *     { "stepName": "validateContent",     "status": "completed", "durationMs": 31  },
 *     { "stepName": "generateSummary",     "status": "running",   "durationMs": null, "attempt": 1 },
 *     { "stepName": "generateEmbeddings",  "status": "running",   "durationMs": null, "attempt": 1 },
 *     { "stepName": "storeSummary",        "status": "pending",   "durationMs": null },
 *     { "stepName": "indexToSearch",       "status": "pending",   "durationMs": null },
 *     { "stepName": "notifySubscribers",   "status": "pending",   "durationMs": null }
 *   ]
 * }
 * }</pre>
 *
 * <p>If {@code generateSummary} fails and retries, the response shows {@code attempt: 2}
 * and {@code retryCount: 1}, giving full visibility into the retry progression.
 *
 * <hr>
 *
 * <h2>3. List and filter runs</h2>
 *
 * <pre>{@code
 * # All completed runs for this workflow
 * GET /workflow-runs?workflowName=news-ingestion&status=completed&page=0&size=20
 *
 * # All failed runs in a time window
 * GET /workflow-runs?workflowName=news-ingestion&status=failed
 *         &from=2026-03-22T00:00:00Z&to=2026-03-22T23:59:59Z
 * }</pre>
 *
 * <hr>
 *
 * <h2>4. Disable the API (embed mode)</h2>
 *
 * <p>Set {@code aetherflow.api.enabled=false} to run the engine without exposing
 * any HTTP endpoints — useful when embedding Aetherflow inside a service that manages
 * its own web layer. In this mode {@code WorkflowRunController} and
 * {@code WorkflowRunQueryService} are not registered as beans at all.
 */
public final class NewsIngestionApiUsageGuide {
	private NewsIngestionApiUsageGuide() {}
}
