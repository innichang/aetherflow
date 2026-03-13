# AetherFlow

Spring-native AI workflow engine for DAG-based orchestration.

AetherFlow is an infrastructure project for teams building AI pipelines in Java and Spring Boot. It focuses on reliable workflow execution for long-running, dependency-driven tasks such as document processing, RAG ingestion, and model inference pipelines.

## Why AetherFlow

Current tools are often not a strong fit for Spring-based AI workloads:

- Schedulers handle time-based jobs, not workflow orchestration.
- Background job frameworks lack DAG execution and AI-focused observability.
- Python-first workflow tools add cross-stack operational complexity.

AetherFlow aims to provide:

- DAG execution inside Spring applications
- Worker-based task runtime
- Durable execution state
- Retry and recovery for long-running AI steps
- Metrics for both system and AI workload behavior

## Core Concepts

- **Workflow**: A full pipeline (for example, `document-processing`)
- **Step**: A single unit of work with dependencies
- **Task**: A schedulable execution of a step
- **Worker**: Runtime process that polls, executes, and reports step progress
- **Execution Store**: Persistent workflow and step state

## High-Level Architecture

1. Spring application defines workflow
2. Engine builds and executes DAG
3. Ready steps are pushed to task queue
4. Workers execute tasks and persist outputs
5. Engine advances workflow state until completion

## MVP Scope (Phase 1)

The first version is intentionally focused:

- Workflow definition API
- DAG execution with step dependencies
- PostgreSQL-backed queue
- Worker runtime
- Retry handling
- Simple dashboard and execution visibility

## Example Workflow

Document processing pipeline:

`upload -> extract -> chunk -> embed -> summarize -> store`

Parallel branches are supported when dependencies allow it.

## Tech Stack

- Java 21
- Spring Boot
- PostgreSQL (execution store + initial queue)
- Micrometer + Prometheus (metrics)
- Grafana (dashboards)

Planned queue evolution:

- Phase 1: PostgreSQL queue
- Phase 2: Redis / Kafka

## Project Modules (Target Layout)

```text
ai-workflow-engine
├─ engine
├─ scheduler
├─ worker-runtime
├─ spring-integration
├─ dashboard
└─ examples
```

## Roadmap

### Phase 1 (MVP)

- Reliable DAG orchestration
- Durable state and retries
- Worker runtime
- Basic operational dashboard

### Phase 2

- AI metrics (model, token, cost, latency)
- Prompt version tracking
- Streaming execution updates
- Resource-aware scheduling

### Phase 3

- GPU-aware scheduling
- Model routing
- Auto-batching
- Distributed workflow execution enhancements

## Design Direction

Primary execution model:

- **State machine + queue hybrid**

This combines queue scalability with explicit workflow state control for reliability and recovery.

## Status

Early-stage design and implementation planning.

If you are interested in contributing, start by reviewing the MVP scope and core engine architecture.

## License

TBD
