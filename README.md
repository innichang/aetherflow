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


## Example Workflow

Document processing pipeline:

`upload -> extract -> chunk -> embed -> summarize -> store`

Parallel branches are supported when dependencies allow it.

## Usage Example

Define a workflow with annotations:

```java
import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import org.springframework.stereotype.Component;

@Component
@AIWorkflow("document-pipeline")
public class DocumentWorkflow {

    @Step
    public void extractText() {}

    @Step(dependsOn = {"extractText"})
    public void chunkText() {}

    @Step(dependsOn = {"chunkText"})
    public void embedChunks() {}
}
```

Start the workflow from application code:

```java
import io.github.inni.aetherflow.engine.WorkflowEngine;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkflowLauncher {

    private final WorkflowEngine workflowEngine;

    public WorkflowLauncher(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    public UUID runDocumentPipeline() {
        return workflowEngine.start("document-pipeline");
    }
}
```

What happens next:

1. AetherFlow scans `@AIWorkflow` + `@Step` definitions at startup.
2. The engine validates the DAG and registers the workflow.
3. `start(...)` creates a workflow run and enqueues root steps.
4. Workers claim tasks using PostgreSQL row locking (`FOR UPDATE SKIP LOCKED`).
5. Completed steps unlock dependent steps until the workflow is complete.

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

## Design Direction

Primary execution model:

- **State machine + queue hybrid**

This combines queue scalability with explicit workflow state control for reliability and recovery.

## Status

Early-stage design and implementation planning.

If you are interested in contributing, start by reviewing the MVP scope and core engine architecture.

## License

TBD
