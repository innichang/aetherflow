# Phase 4 - Observability and Debugging

## Goal

Make workflow execution understandable, traceable, and debuggable for developers and operators.

This phase turns AetherFlow from "engine that runs tasks" into "platform teams can confidently operate in production."

## Milestone Outcome

Developers can inspect workflow runs, diagnose step failures quickly, and monitor system health using logs, metrics, and a visual run interface.

## Scope

### 1) Execution Logs

Capture structured logs for:
- workflow start and completion
- step start and completion
- step failures and retry attempts
- execution duration per step

Required logging fields:
- `workflow_run_id`
- `step_run_id`
- `workflow_name`
- `step_name`
- `worker_id`
- `attempt`
- `status`
- `duration_ms`
- `error_type` and `error_message` (on failure)

Recommended behavior:
- ensure logs are correlated with IDs used in database records
- keep logs machine-readable (JSON format preferred)

### 2) Workflow Visualization

Provide DAG and run-state visualization:
- graph view of workflow definition
- current run status per step (pending/running/completed/failed/retrying)
- dependency paths and blocked nodes

Minimum visual states:
- not started
- queued
- running
- succeeded
- failed
- timed out

### 3) Metrics and Monitoring

Expose operational metrics for reliability tuning.

Core metrics:
- task throughput (tasks/sec)
- queue length by status
- step success/failure rates
- retry rate
- worker utilization
- p50/p95/p99 step execution latency

Integrations:
- Prometheus metrics endpoint
- Grafana dashboard templates

### 4) Web Dashboard

Build a lightweight dashboard for day-to-day operations.

Primary sections:
- workflows list
- workflow runs list
- run detail page with step graph
- step log and error detail viewer

Essential interactions:
- filter runs by status/workflow/time
- inspect individual step execution attempts
- view failure reason and retry history

## Suggested Architecture

Add an observability layer that reads from:
- execution event stream (or persisted run tables)
- log backend
- metrics registry

Recommended components:
- `ExecutionEventPublisher`
- `RunStateProjector`
- `MetricsCollector`
- `DashboardController` / `DashboardAPI`

## Definition of Done

Phase 4 is complete when all are true:
- every step execution emits correlated logs
- operators can inspect run/step state in near real time
- Prometheus endpoint exposes core queue/worker/latency metrics
- a dashboard shows DAG and per-step execution outcomes
- failures can be diagnosed without direct DB querying

## Demo Scenario

1. trigger a workflow with one intentionally failing step
2. open dashboard and observe live step transitions
3. inspect failure logs with correlated run IDs
4. view queue/throughput/failure metrics in Grafana
5. confirm retry and final status are visible in both logs and UI
