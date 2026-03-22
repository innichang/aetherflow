# AetherFlow

Spring-native workflow engine for DAG-based orchestration of long-running tasks.

AetherFlow is built for teams running AI pipelines in Java and Spring Boot — document processing, RAG ingestion, model inference — where reliable, retryable, dependency-ordered execution matters.

## Why AetherFlow

- Standard schedulers handle time-based jobs, not DAG workflows.
- Background job frameworks lack step dependency tracking and AI-focused reliability features.
- Python-first workflow tools add cross-stack complexity.

AetherFlow provides:

- DAG execution with `@AIWorkflow` + `@Step` annotations
- PostgreSQL-backed task queue with `FOR UPDATE SKIP LOCKED` distributed locking
- Retries with exponential backoff, step timeouts, and dead worker recovery
- REST API to start and inspect workflow runs
- Cron and Kafka triggers out of the box
- Spring Boot auto-configuration

---

## Quickstart

### Prerequisites

- Java 17+
- PostgreSQL database
- Docker (for integration tests)

### 1. Configure your database

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aetherflow
    username: postgres
    password: postgres

aetherflow:
  worker:
    poll-interval-ms: 250
```

Flyway migrations run automatically on startup and create all required tables.

### 2. Define a workflow

Annotate a Spring `@Component` with `@AIWorkflow`. Each method annotated with `@Step` is one node in the DAG.

```java
@Component
@AIWorkflow("document-pipeline")
public class DocumentPipelineWorkflow {

    @Step
    public void extractText() {
        // step logic here
    }

    @Step(dependsOn = "extractText", retries = 3, backoff = "5s")
    public void chunkText() {}

    @Step(dependsOn = "extractText", retries = 3, backoff = "5s")
    public void generateEmbeddings() {}

    @Step(dependsOn = {"chunkText", "generateEmbeddings"}, timeoutSeconds = 60)
    public void storeResults() {}
}
```

The engine validates the DAG at startup — cycles, missing dependencies, and duplicate step names all fail fast.

### 3. Start a workflow via the REST API

```http
POST /workflows/start
Content-Type: application/json

{
  "workflowName": "document-pipeline",
  "input": { "documentId": "doc-123" }
}
```

Response:

```json
{
  "runId": "a3f1c2d4-...",
  "workflowName": "document-pipeline",
  "startedAt": "2026-03-22T10:00:00Z"
}
```

### 4. Observe run progress

```http
GET /workflow-runs/a3f1c2d4-...
```

```json
{
  "runId": "a3f1c2d4-...",
  "workflowName": "document-pipeline",
  "status": "completed",
  "startedAt": "2026-03-22T10:00:00Z",
  "completedAt": "2026-03-22T10:00:04Z",
  "steps": [
    { "stepName": "extractText",        "status": "completed", "durationMs": 812 },
    { "stepName": "chunkText",          "status": "completed", "durationMs": 1340 },
    { "stepName": "generateEmbeddings", "status": "completed", "durationMs": 2100 },
    { "stepName": "storeResults",       "status": "completed", "durationMs": 430 }
  ]
}
```

### 5. List runs

```http
GET /workflow-runs?workflowName=document-pipeline&status=completed&page=0&size=20
```

---

## Step Annotation Reference

| Attribute        | Type     | Default | Description                                           |
|------------------|----------|---------|-------------------------------------------------------|
| `name`           | String   | method name | Override the step name                           |
| `dependsOn`      | String[] | `{}`    | Names of steps that must complete before this one     |
| `retries`        | int      | `0`     | Maximum retry attempts on failure                     |
| `backoff`        | String   | `""`    | Exponential backoff base, e.g. `"5s"`, `"2m"`        |
| `timeoutSeconds` | int      | `0`     | Step execution timeout (0 = no timeout)               |
| `concurrency`    | int      | `0`     | Max parallel executions of this step (0 = unlimited)  |

---

## Triggers

### Cron

Start workflows on a schedule:

```yaml
aetherflow:
  triggers:
    cron:
      - workflow: document-pipeline
        schedule: "0 0 * * * *"   # top of every hour
        input: '{"source":"scheduled"}'
```

Spring cron format: `second minute hour day month weekday`.

### Kafka

Start workflows from Kafka messages:

```yaml
aetherflow:
  triggers:
    kafka:
      - topic: documents.uploaded
        workflow: document-pipeline
        deduplication-key-field: documentId
```

When `deduplication-key-field` is set, duplicate messages with the same field value are silently skipped.

---

## Configuration Reference

```yaml
aetherflow:
  worker:
    poll-interval-ms: 250          # how often each worker polls for tasks
    heartbeat-interval-ms: 5000    # how often workers emit heartbeats
    liveness-timeout-ms: 30000     # time before a silent worker is marked dead
    liveness-check-interval-ms: 15000
    recovery-interval-ms: 10000    # how often expired task locks are reclaimed

  retry:
    default-retries: 0             # fallback if @Step.retries not set
    default-backoff-seconds: 5

  queue:
    batch-size: 1
    lock-timeout-seconds: 25       # how long a claimed task is locked

  api:
    enabled: true                  # set false to disable REST endpoints
```

---

## Build & Test

```bash
./gradlew build          # compile and run all tests
./gradlew test           # run tests only
./gradlew clean build    # clean then build
```

Integration tests require Docker (Testcontainers PostgreSQL + Kafka). They are skipped automatically when Docker is unavailable.

---

## Architecture

### Workflow Definition

Workflows are plain Spring `@Component` beans annotated with `@AIWorkflow("name")`. Steps are methods annotated with `@Step`.

### Startup Lifecycle

1. `WorkflowScanner` discovers all `@AIWorkflow` beans at startup.
2. `DependencyGraphBuilder` validates the DAG (Kahn's topological sort — detects cycles, missing references, duplicates).
3. `WorkflowRegistry` stores validated workflows in memory.
4. `WorkflowMetadataInitializer` syncs workflow and step metadata to the database.

### Execution Flow

`WorkflowEngine.start(name)` creates a `WorkflowRunEntity` and enqueues root steps (no dependencies) into `task_queue`.

`TaskPoller` (default every 250ms) claims tasks via `FOR UPDATE SKIP LOCKED` — PostgreSQL row-level locking is the distributed coordination mechanism; no separate lock service required.

After each step executes, `ResultReporter` either:
- **On success**: checks which dependent steps are now fully unblocked and enqueues them.
- **On failure**: retries with exponential backoff if retries remain; otherwise marks the step and workflow run as `failed`.

`TimeoutRecoveryScheduler` runs two sweeps periodically:
- Reclaims tasks whose lock has expired (worker crashed mid-execution).
- Marks workers that have stopped sending heartbeats as dead and re-routes their in-flight tasks.

### Key Packages

| Package | Responsibility |
|---|---|
| `engine/` | `WorkflowEngine`, `ExecutionStatus` |
| `workflow/annotation/` | `@AIWorkflow`, `@Step` |
| `workflow/graph/` | DAG building and validation |
| `workflow/registry/` | Discovery, in-memory registry, reflection invokers |
| `execution/` | Worker polling, task claiming, step execution, result/dependency fanout |
| `persistence/` | JPA entities, repositories, Flyway migrations |
| `api/` | REST endpoints and query service |
| `config/` | `AetherflowAutoConfiguration`, properties, cron/Kafka trigger wiring |
| `examples/` | `order-fulfillment` demo workflow |

### Database Tables

| Table | Purpose |
|---|---|
| `workflows` | Static workflow metadata |
| `steps` | Static step metadata (retries, timeout, backoff, concurrency) |
| `workflow_runs` | One row per run; holds status, input/output payload, trigger key |
| `step_runs` | One row per step execution attempt |
| `task_queue` | Live worker queue, polled with `FOR UPDATE SKIP LOCKED` |
| `workers` | Registered worker instances with heartbeat timestamps |
| `worker_heartbeats` | Heartbeat log for liveness tracking |

---

## Status

Phase 1 (core engine) and Phase 2 (reliability — retries, timeouts, heartbeats) are complete.
Phase 3 (Spring ecosystem integration — REST API, cron/Kafka triggers, auto-configuration) is complete.

Planned next:
- Phase 4: Observability — step latency metrics, queue depth, retry rate dashboards
- Phase 5: Multi-module packaging — `aetherflow-core` + `aetherflow-spring-boot-starter` JARs
- Phase 6: Management UI

## License

TBD
