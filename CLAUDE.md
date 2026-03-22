# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew build          # compile and run all tests
./gradlew test           # run tests only
./gradlew clean build    # clean then build
```

Integration tests require Docker (for Testcontainers PostgreSQL). They are conditionally skipped when Docker is unavailable.

## Architecture

Aetherflow is a **DAG-based workflow orchestration engine** built on Spring Boot 4 with PostgreSQL as the task queue backend.

### Workflow Definition

Workflows are plain Spring `@Component` beans annotated with `@AIWorkflow("name")`. Individual steps are methods annotated with `@Step`, which supports `dependsOn`, `retries`, `timeoutSeconds`, and `concurrency` attributes.

### Startup Lifecycle

1. `WorkflowScanner` (via `ApplicationContext`) discovers all `@AIWorkflow` beans at startup
2. `DependencyGraphBuilder` validates the DAG: checks for cycles, duplicate step names, and unresolved `dependsOn` references using Kahn's topological sort algorithm
3. `WorkflowRegistry` stores the validated `RegisteredWorkflow` records (workflow metadata + DAG + `StepInvoker` method handles)
4. `WorkflowMetadataInitializer` syncs registered workflows to the `workflows` and `steps` DB tables via `WorkflowMetadataSyncService`

### Execution Flow

`WorkflowEngine.start(workflowName)` creates a `WorkflowRunEntity` and enqueues only the **root steps** (steps with no dependencies) into the `task_queue` table.

The `TaskPoller` (scheduled at `aetherflow.worker.poll-interval-ms`, default 250ms) claims tasks using `FOR UPDATE SKIP LOCKED` (via `TaskQueueClaimService`) — this is the distributed locking mechanism that allows multiple workers without a separate lock service.

After each step executes (`TaskExecutor` invokes the method via reflection), `ResultReporter` either:
- **On success**: checks each dependent step to see if all its dependencies are now complete, and enqueues newly unblocked steps
- **On failure**: marks the step, task, and entire workflow run as FAILED

### Key Packages

| Package | Responsibility |
|---|---|
| `engine/` | `WorkflowEngine` (start), `ExecutionStatus` constants |
| `workflow/annotation/` | `@AIWorkflow`, `@Step` |
| `workflow/graph/` | DAG building and validation |
| `workflow/registry/` | Discovery (`WorkflowScanner`), in-memory registry, reflection invokers |
| `execution/` | Worker polling, task claiming, step execution, result/dependency fanout |
| `persistence/` | JPA entities, repositories, metadata sync to DB |
| `examples/` | `order_fulfillment` demo workflow |

### Database

PostgreSQL with Flyway migrations (`src/main/resources/db/migration/`). Five core tables:
- `workflows`, `steps` — static workflow/step metadata
- `workflow_runs`, `step_runs` — execution history
- `task_queue` — live worker queue (polled with `FOR UPDATE SKIP LOCKED`)

Key index for performance: `idx_task_queue_poll(status, available_at, priority)`.

### Configuration

```yaml
aetherflow:
  worker:
    poll-interval-ms: 250
```

DB connection via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars (defaults to `localhost:5432/aetherflow`).
