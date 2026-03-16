# Phase 3 - Spring Ecosystem Integration

## Goal

Make AetherFlow feel native to Spring developers so adoption is low-friction.

This phase shifts from "engine works" to "framework is easy to install, configure, and integrate with services."

## Milestone Outcome

A developer can add one dependency, configure a few properties, and trigger/query workflow runs through standard Spring Boot APIs.

## Scope

### 1) Spring Boot Starter

Create starter module:
- `aetherflow-spring-boot-starter`

Provide auto-configuration for:
- `WorkflowEngine`
- `TaskQueue`
- `WorkerService`
- repositories and persistence adapters

Target usage:

```kotlin
implementation("aetherflow-spring-boot-starter")
```

Auto-configuration requirements:
- conditional bean creation (`@ConditionalOnMissingBean`)
- feature toggles via properties (`@ConditionalOnProperty`)
- sensible defaults with override options

### 2) Configuration System

Support externalized settings with `@ConfigurationProperties`.

Example:

```yaml
aetherflow:
  worker:
    threads: 10
  retry:
    default: 3
```

Recommended property groups:
- `aetherflow.worker.*` (threads, polling interval, heartbeat interval)
- `aetherflow.retry.*` (default retries, backoff policy)
- `aetherflow.queue.*` (batch size, lock timeout)
- `aetherflow.api.*` (endpoint enablement/security defaults)

### 3) Workflow Trigger APIs

Expose HTTP APIs for orchestration control.

Required endpoints:
- `POST /workflows/start`
- `GET /workflow-runs`
- `GET /workflow-runs/{id}`

Minimum API behavior:
- start workflow by name with optional input payload
- list/filter run history (status, workflow, date range)
- return run details including step statuses and timestamps

Use cases enabled:
- external service triggering
- microservice-to-workflow integration
- event gateway initiation

### 4) Event-Based Triggers

Allow workflows to start from external events:
- HTTP events/webhooks
- cron schedules
- Kafka messages (optional but recommended)

Design expectations:
- trigger adapters convert incoming events into start requests
- payload mapping into workflow input context
- deduplication safeguards for at-least-once event delivery

## Developer Experience Requirements

By end of Phase 3, a new user should be able to:
1. add starter dependency
2. set minimal `application.yaml` config
3. annotate workflow class with `@AIWorkflow` + `@Step`
4. start workflow through API
5. inspect run state through API responses

## Definition of Done

Phase 3 is complete when all are true:
- starter module auto-configures core beans in a fresh Spring Boot app
- properties bind cleanly with documented defaults
- REST APIs can start workflows and query run details
- at least one non-HTTP trigger path (cron or Kafka) is operational
- integration tests validate starter bootstrapping and API contract
- quickstart documentation enables first successful run in minutes

## Demo Scenario

1. create new Spring Boot sample app
2. add `aetherflow-spring-boot-starter`
3. configure worker and retry settings in `application.yaml`
4. call `POST /workflows/start` for `document-pipeline`
5. query `GET /workflow-runs/{id}` to observe progress and completion
6. trigger same workflow from scheduled job or Kafka message
