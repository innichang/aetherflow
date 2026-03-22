CREATE TABLE workers (
    id              VARCHAR(255) PRIMARY KEY,
    hostname        VARCHAR(255),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_heartbeat  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status          VARCHAR(50) NOT NULL DEFAULT 'active'
);

CREATE TABLE worker_heartbeats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id   VARCHAR(255) NOT NULL REFERENCES workers(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_worker_heartbeats_worker_time
    ON worker_heartbeats (worker_id, recorded_at DESC);

CREATE INDEX idx_workers_last_heartbeat
    ON workers (status, last_heartbeat);
