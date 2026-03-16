# Phase 2 - Reliability and Production Features

## Goal

Make the Phase 1 engine reliable enough for real-world services.

This phase hardens execution behavior so that workflows remain correct and recoverable under failure, latency, and distributed worker conditions.

## Milestone Outcome

AetherFlow supports retry policies, timeout handling, heartbeat-based worker recovery, concurrency limits, and idempotent execution semantics.

At the end of Phase 2, the engine should be comparable to the reliability baseline of early JobRunr or Quartz-style production schedulers.

## Scope

### 1) Retry System

Support retry configuration at step level:

```java
@Step(retries = 3, backoff = "5s")
```

Required capabilities:
- retry counters per `step_run`
- maximum retry enforcement
- exponential backoff scheduling
- retry task re-enqueueing with `next_retry_time`

Required persistence fields:
- `retry_count`
- `max_retries`
- `next_retry_time`
- `last_error` (recommended for observability and debugging)

### 2) Timeout Handling

Prevent stuck execution by enforcing step time limits.

Required capabilities:
- configurable step timeout
- running task timeout detection
- timeout status persistence
- automatic recovery/requeue of timed-out tasks

Recovery behavior:
- if worker crashes or hangs, task lock is considered expired
- eligible task returns to queue for reassignment

### 3) Worker Heartbeat and Liveness

Workers must periodically publish health:
- `worker_id`
- `last_seen`
- `status`

Required tables:
- `workers`
- `worker_heartbeats`

Required capabilities:
- heartbeat emission on interval
- dead worker detection via stale heartbeat window
- orphaned task recovery for tasks owned by dead workers

### 4) Concurrency Controls

Add step-level concurrency limits:

```java
@Step(concurrency = 5)
```

Purpose:
- avoid API rate-limit bursts
- prevent GPU or infra saturation
- smooth high-volume LLM fan-out workloads

Required behavior:
- scheduler checks active count for step key before enqueue/dispatch
- if limit reached, defer task with future availability timestamp

### 5) Idempotency and Safe Re-execution

Retries and recovery must not cause duplicate side effects.

Required capabilities:
- idempotency key generation per logical step execution
- execution checkpoints for resumable operations
- duplicate execution guard at persistence boundary

Recommended persistence:
- `idempotency_key` on `step_runs` (unique index scope by workflow run + step)
- checkpoint payload/state metadata for resumable steps

## Reliability Architecture Updates

Enhance runtime components from Phase 1:
- `TaskPoller` respects availability/timeout/retry windows
- `TaskExecutor` updates heartbeat ownership and checkpoint writes
- `ResultReporter` handles retry scheduling and terminal failure status
- background recovery service reclaims timed-out/orphaned tasks

## Failure Scenarios You Must Pass

1. transient step failure succeeds on retry before max retries
2. step keeps failing and transitions to terminal failed state after max retries
3. worker process dies mid-task and task is recovered by another worker
4. step exceeds timeout and is marked timed-out then retried/recovered
5. duplicate dispatch attempt does not create duplicate side effects
6. concurrency-limited step never exceeds configured active execution count

## Definition of Done

Phase 2 is complete when all are true:
- retry policy works with exponential backoff and persistence
- timeout and dead-worker recovery return tasks to runnable state
- heartbeats are stored and monitored for worker liveness
- concurrency limits are enforced under load
- idempotency protections prevent duplicate execution effects
- integration tests cover crash/retry/timeout/concurrency scenarios

## Demo Scenario

Demonstrate reliability with controlled failures:
1. run workflow with one intentionally flaky step
2. show retries and backoff timestamps in DB
3. kill a worker during execution and show task recovery
4. run load test with `@Step(concurrency = N)` and show cap enforcement
5. show idempotency key preventing duplicate result writes
