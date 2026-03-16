# AetherFlow Database Schema Design

## Purpose

This document defines the PostgreSQL schema for AetherFlow and explains each table's responsibility.

It is written to be:
- implementation-ready for Flyway migrations
- aligned with roadmap phases (`phase_1` through `phase_6`)
- explicit about keys, constraints, and indexes for orchestration workloads

## Design Principles

- Use PostgreSQL `UUID` primary keys for distributed-safe identifiers.
- Keep immutable definitions (`workflows`, `steps`) separate from runtime state (`workflow_runs`, `step_runs`, `task_queue`).
- Optimize worker polling with `status` + `available_at` indexes.
- Enforce data integrity with foreign keys and uniqueness constraints.
- Support future extensibility (AI usage, tenants, versioned workflows).

## Conventions

- Timestamp columns use `TIMESTAMPTZ`.
- Status fields use `VARCHAR` with check constraints (or can be migrated to PostgreSQL enums later).
- JSON payloads use `JSONB`.
- Snake case naming for all tables and columns.

---

## Phase 1 (MVP) Core Schema

### 1) `workflows`

Stores workflow definitions discovered from Spring annotations.

```sql
CREATE TABLE workflows (
    id                  UUID PRIMARY KEY,
    workflow_name       VARCHAR(255) NOT NULL UNIQUE,
    workflow_version    INTEGER NOT NULL DEFAULT 1,
    description         TEXT,
    bean_name           VARCHAR(255) NOT NULL,
    class_name          VARCHAR(512) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 2) `steps`

Stores step metadata for each workflow definition.

```sql
CREATE TABLE steps (
    id                  UUID PRIMARY KEY,
    workflow_id         UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    step_name           VARCHAR(255) NOT NULL,
    method_name         VARCHAR(255) NOT NULL,
    step_order_hint     INTEGER,
    depends_on          TEXT[] NOT NULL DEFAULT '{}',
    retries             INTEGER NOT NULL DEFAULT 0,
    timeout_seconds     INTEGER,
    concurrency_limit   INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_steps_workflow_step UNIQUE (workflow_id, step_name)
);
```

### 3) `workflow_runs`

One row per workflow execution.

```sql
CREATE TABLE workflow_runs (
    id                  UUID PRIMARY KEY,
    workflow_id         UUID NOT NULL REFERENCES workflows(id),
    workflow_name       VARCHAR(255) NOT NULL,
    workflow_version    INTEGER NOT NULL,
    status              VARCHAR(50) NOT NULL,
    input_payload       JSONB,
    output_payload      JSONB,
    error_message       TEXT,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 4) `step_runs`

Execution record per step attempt lifecycle (logical step execution within a workflow run).

```sql
CREATE TABLE step_runs (
    id                  UUID PRIMARY KEY,
    workflow_run_id     UUID NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
    workflow_id         UUID NOT NULL REFERENCES workflows(id),
    step_id             UUID NOT NULL REFERENCES steps(id),
    step_name           VARCHAR(255) NOT NULL,
    status              VARCHAR(50) NOT NULL,
    attempt             INTEGER NOT NULL DEFAULT 1,
    max_retries         INTEGER NOT NULL DEFAULT 0,
    retry_count         INTEGER NOT NULL DEFAULT 0,
    next_retry_time     TIMESTAMPTZ,
    idempotency_key     VARCHAR(255),
    worker_id           VARCHAR(255),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    duration_ms         BIGINT,
    input_payload       JSONB,
    output_payload      JSONB,
    error_type          VARCHAR(255),
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_step_runs_logical UNIQUE (workflow_run_id, step_name, attempt)
);
```

### 5) `task_queue`

Worker queue table used by `FOR UPDATE SKIP LOCKED`.

```sql
CREATE TABLE task_queue (
    id                  UUID PRIMARY KEY,
    workflow_run_id     UUID NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
    step_run_id         UUID NOT NULL REFERENCES step_runs(id) ON DELETE CASCADE,
    workflow_name       VARCHAR(255) NOT NULL,
    step_name           VARCHAR(255) NOT NULL,
    status              VARCHAR(50) NOT NULL,
    priority            INTEGER NOT NULL DEFAULT 100,
    available_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_at           TIMESTAMPTZ,
    lock_expires_at     TIMESTAMPTZ,
    worker_id           VARCHAR(255),
    queue_message       JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Core Indexes (Phase 1 + immediate reliability support)

```sql
CREATE INDEX idx_workflow_runs_lookup
    ON workflow_runs (workflow_name, created_at DESC);

CREATE INDEX idx_step_runs_workflow_run
    ON step_runs (workflow_run_id, step_name, status);

CREATE INDEX idx_step_runs_retry
    ON step_runs (status, next_retry_time);

CREATE INDEX idx_task_queue_poll
    ON task_queue (status, available_at, priority);

CREATE INDEX idx_task_queue_lock_recovery
    ON task_queue (status, lock_expires_at);

CREATE INDEX idx_task_queue_workflow_run
    ON task_queue (workflow_run_id);
```

---

## Phase 2 Reliability Tables

### 6) `workers`

Worker registry and static metadata.

```sql
CREATE TABLE workers (
    worker_id           VARCHAR(255) PRIMARY KEY,
    host_name           VARCHAR(255),
    process_id          VARCHAR(100),
    status              VARCHAR(50) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 7) `worker_heartbeats`

Time-series heartbeat history for liveness analysis and debugging.

```sql
CREATE TABLE worker_heartbeats (
    id                  UUID PRIMARY KEY,
    worker_id           VARCHAR(255) NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    status              VARCHAR(50) NOT NULL,
    heartbeat_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    metadata            JSONB
);
```

### Reliability Indexes

```sql
CREATE INDEX idx_workers_last_seen
    ON workers (last_seen_at);

CREATE INDEX idx_worker_heartbeats_worker_time
    ON worker_heartbeats (worker_id, heartbeat_at DESC);
```

---

## Phase 3 API and Trigger Support

No mandatory new tables are required for basic REST APIs if run and step state are already modeled.

Optional trigger table:

### 8) `workflow_triggers` (optional)

```sql
CREATE TABLE workflow_triggers (
    id                  UUID PRIMARY KEY,
    workflow_id         UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    trigger_type        VARCHAR(50) NOT NULL,   -- http, cron, kafka
    trigger_name        VARCHAR(255) NOT NULL,
    trigger_config      JSONB NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workflow_trigger UNIQUE (workflow_id, trigger_type, trigger_name)
);
```

---

## Phase 4 Observability Extensions

### 9) `step_logs` (optional if external log system is used)

If logs are not stored externally, persist structured execution logs:

```sql
CREATE TABLE step_logs (
    id                  UUID PRIMARY KEY,
    workflow_run_id     UUID NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
    step_run_id         UUID NOT NULL REFERENCES step_runs(id) ON DELETE CASCADE,
    level               VARCHAR(20) NOT NULL,
    message             TEXT NOT NULL,
    log_payload         JSONB,
    logged_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Index:

```sql
CREATE INDEX idx_step_logs_step_run_time
    ON step_logs (step_run_id, logged_at DESC);
```

---

## Phase 5 AI-Native Extensions

### 10) `ai_step_usage`

Stores LLM usage and billing-relevant metrics per step run.

```sql
CREATE TABLE ai_step_usage (
    id                  UUID PRIMARY KEY,
    step_run_id         UUID NOT NULL REFERENCES step_runs(id) ON DELETE CASCADE UNIQUE,
    provider            VARCHAR(100) NOT NULL,
    model_name          VARCHAR(255) NOT NULL,
    model_version       VARCHAR(100),
    prompt_version      VARCHAR(100),
    prompt_template_id  VARCHAR(255),
    temperature         NUMERIC(5,3),
    top_p               NUMERIC(5,3),
    max_tokens          INTEGER,
    prompt_tokens       INTEGER NOT NULL DEFAULT 0,
    completion_tokens   INTEGER NOT NULL DEFAULT 0,
    total_tokens        INTEGER NOT NULL DEFAULT 0,
    latency_ms          BIGINT,
    estimated_cost_usd  NUMERIC(12,6),
    actual_cost_usd     NUMERIC(12,6),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Index:

```sql
CREATE INDEX idx_ai_step_usage_provider_model
    ON ai_step_usage (provider, model_name, created_at DESC);
```

---

## Phase 6 Distributed / Multi-Tenant Extensions

### 11) Tenant support columns

For multi-tenant isolation, add `tenant_id` to:
- `workflows`
- `workflow_runs`
- `step_runs`
- `task_queue`

Recommended unique/index constraints after tenant enablement:

```sql
-- Example (migration change)
-- ALTER TABLE workflows ADD COLUMN tenant_id VARCHAR(100);
-- CREATE UNIQUE INDEX uq_workflows_tenant_name_version
--   ON workflows (tenant_id, workflow_name, workflow_version);
```

### 12) Workflow versioning hardening

Use immutable definitions with explicit active flag:
- keep multiple versions in `workflows`
- pin `workflow_runs.workflow_version`

Optional rollout table:

```sql
CREATE TABLE workflow_release_channels (
    id                  UUID PRIMARY KEY,
    tenant_id           VARCHAR(100),
    workflow_name       VARCHAR(255) NOT NULL,
    channel_name        VARCHAR(100) NOT NULL, -- stable, canary, etc.
    workflow_version    INTEGER NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_release_channel UNIQUE (tenant_id, workflow_name, channel_name)
);
```

---

## Status Values (Recommended)

### Workflow and step statuses

- `pending`
- `queued`
- `running`
- `retrying`
- `completed`
- `failed`
- `timed_out`
- `cancelled`

### Worker statuses

- `active`
- `degraded`
- `draining`
- `offline`

---

## Transactional Patterns

### Safe task claim

```sql
SELECT id
FROM task_queue
WHERE status = 'pending'
  AND available_at <= NOW()
ORDER BY priority ASC, available_at ASC
FOR UPDATE SKIP LOCKED
LIMIT 1;
```

### Claim + mark running in one transaction

- select candidate task with lock
- update `task_queue.status = 'running'`, set `worker_id`, `locked_at`, `lock_expires_at`
- update corresponding `step_runs.status = 'running'`, set `started_at`

---

## Flyway Migration Plan (Suggested)

- `V1__create_core_workflow_tables.sql`
  - `workflows`, `steps`, `workflow_runs`, `step_runs`, `task_queue`
  - core indexes and constraints
- `V2__add_worker_and_heartbeat_tables.sql`
  - `workers`, `worker_heartbeats`, reliability indexes
- `V3__add_trigger_and_observability_tables.sql`
  - optional `workflow_triggers`, `step_logs`
- `V4__add_ai_usage_tables.sql`
  - `ai_step_usage`
- `V5__add_multitenancy_and_versioning.sql`
  - `tenant_id` expansions, release channels, related indexes

This sequence keeps Phase 1 deployable early while allowing incremental expansion in later phases.
