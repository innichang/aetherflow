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

CREATE TABLE steps (
    id                  UUID PRIMARY KEY,
    workflow_id         UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    step_name           VARCHAR(255) NOT NULL,
    method_name         VARCHAR(255) NOT NULL,
    step_order_hint     INTEGER,
    depends_on          TEXT NOT NULL DEFAULT '',
    retries             INTEGER NOT NULL DEFAULT 0,
    timeout_seconds     INTEGER,
    concurrency_limit   INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_steps_workflow_step UNIQUE (workflow_id, step_name)
);

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

