package io.github.inni.aetherflow.examples.phase_3_news_ingestion_pipeline.trigger;

/**
 * Phase 3 trigger configuration guide for the news-ingestion workflow.
 *
 * <p>No Java code is needed to wire triggers — they are declared entirely in
 * {@code application.yaml}. This class exists as a readable reference for the
 * config blocks that activate cron and Kafka triggers for this example workflow.
 *
 * <p>Add the following to your {@code application.yaml} (or a profile-specific
 * override) to enable both trigger types:
 *
 * <pre>{@code
 * aetherflow:
 *
 *   # ── Worker tuning ──────────────────────────────────────────────────────
 *   worker:
 *     poll-interval-ms: 250          # how often each worker polls for tasks
 *     heartbeat-interval-ms: 5000
 *     liveness-timeout-ms: 30000
 *
 *   # ── Retry defaults (overridden per-step via @Step annotation) ──────────
 *   retry:
 *     default-retries: 0
 *     default-backoff-seconds: 5
 *
 *   # ── Task queue ─────────────────────────────────────────────────────────
 *   queue:
 *     batch-size: 1
 *     lock-timeout-seconds: 25
 *
 *   # ── REST API (POST /workflows/start, GET /workflow-runs/**) ────────────
 *   api:
 *     enabled: true                  # set false to disable REST endpoints
 *
 *   # ── Triggers ───────────────────────────────────────────────────────────
 *   triggers:
 *
 *     # Cron: re-run the pipeline every night at midnight (catch-up for missed articles)
 *     # Spring cron format: second minute hour day-of-month month day-of-week
 *     cron:
 *       - workflow: news-ingestion
 *         schedule: "0 0 0 * * *"
 *         input: '{"source":"scheduled-catchup"}'
 *
 *     # Kafka: start a run for every message on articles.submitted
 *     # deduplication-key-field: the JSON field used as the trigger key —
 *     #   if a run with the same articleId already exists it is silently skipped,
 *     #   even if two messages arrive simultaneously (race-safe via partial unique index)
 *     kafka:
 *       - topic: articles.submitted
 *         workflow: news-ingestion
 *         deduplication-key-field: articleId
 * }</pre>
 *
 * <p>Example Kafka message payload (published to {@code articles.submitted}):
 * <pre>{@code
 * {
 *   "articleId": "art-2026-03-22-001",
 *   "sourceUrl": "https://example.com/articles/spring-ai-pipelines",
 *   "publishedAt": "2026-03-22T09:00:00Z"
 * }
 * }</pre>
 *
 * <p>The engine will:
 * <ol>
 *   <li>Extract {@code articleId} from the payload as the trigger key.
 *   <li>Skip the run if a workflow run with that key already exists (deduplication).
 *   <li>Otherwise start a new {@code news-ingestion} run with the full JSON payload
 *       as input, which steps can read from the workflow run's {@code inputPayload}.
 * </ol>
 *
 * <p>Observing runs via the built-in REST API (no custom controller needed):
 * <pre>{@code
 * # Start manually
 * POST /workflows/start
 * {"workflowName":"news-ingestion","input":{"articleId":"art-123","sourceUrl":"..."}}
 *
 * # Get a specific run
 * GET /workflow-runs/{runId}
 *
 * # List all completed runs for this workflow
 * GET /workflow-runs?workflowName=news-ingestion&status=completed&page=0&size=20
 * }</pre>
 */
public final class NewsIngestionTriggerGuide {
	private NewsIngestionTriggerGuide() {}
}
