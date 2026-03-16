# Phase 1 - Core Workflow Engine (MVP)

## Goal

Build the minimum orchestration engine that can execute a DAG workflow end-to-end.

This phase validates the core of AetherFlow:
- define workflows in code
- discover and register workflow metadata
- persist workflow and execution state
- schedule executable tasks
- run steps in dependency order

If this phase is not complete, later reliability and ecosystem features have no stable foundation.

## Milestone Outcome

A developer can run:

```java
workflowEngine.start("document-pipeline");
```

and the engine executes the workflow steps in valid DAG order with persistent state in PostgreSQL.

## Scope

### 1) Workflow Definition System

Developers should define workflows with annotations:

```java
@AIWorkflow("document-pipeline")
class DocumentWorkflow {

    @Step
    public void extractText() {}

    @Step(dependsOn = "extractText")
    public void chunkText() {}

    @Step(dependsOn = "chunkText")
    public void embedChunks() {}
}
```

Implement the core model and discovery components:
- `WorkflowScanner`
- `WorkflowDefinition`
- `StepDefinition`
- `DependencyGraphBuilder`
- `WorkflowRegistry`

Required behavior:
1. scan Spring beans during startup
2. detect `@AIWorkflow` and `@Step` methods
3. build canonical workflow definitions
4. build and validate DAG dependencies
5. register executable workflows in-memory for runtime lookup

### 2) DAG Graph Construction and Validation

Represent each workflow as a dependency graph:
- `StepNode`
- `StepEdge`
- `DependencyGraph`
Validation requirements:
- detect circular dependencies (fail startup/registration)
- detect missing `dependsOn` references
- detect invalid or duplicate step names
- produce deterministic topological execution order

### 3) PostgreSQL Persistence Layer

Persist both static metadata and runtime execution state.

Required tables:
- `workflows` (workflow definitions)
- `workflow_runs` (workflow execution instances)
- `steps` (step metadata)
- `step_runs` (per-step execution record)
- `task_queue` (pending/running/completed task entries)

Minimum indexing strategy:
- `task_queue(status, available_at)` for worker polling
- `task_queue(workflow_run_id)` for execution traceability
- `workflow_runs(workflow_name, created_at)` for history lookup
- `step_runs(workflow_run_id, step_name)` for run-level status inspection

### 4) Task Queue System

Workers must pull tasks safely under concurrent load using:

```sql
SELECT *
FROM task_queue
WHERE status = 'pending'
FOR UPDATE SKIP LOCKED
LIMIT 1;
```

Queue flow:
1. worker polls next pending task
2. transaction locks task row
3. task marked running
4. step executes
5. result and status persisted
6. downstream steps enqueued when dependencies are satisfied

### 5) Worker Execution Runtime

Implement execution runtime components:
- `TaskPoller`
- `TaskExecutor`
- `ResultReporter`

Runtime responsibilities:
- poll task queue continuously
- locate workflow step method via registry
- execute step method on Spring bean
- capture execution duration
- persist success/failure in `step_runs`
- enqueue next eligible steps
- support retries metadata (even if policy is minimal in Phase 1)

## Suggested Execution Flow (Reference)

1. `workflowEngine.start(workflowName)` creates `workflow_run`
2. root steps (no dependencies) are inserted into `task_queue`
3. worker claims one task using `FOR UPDATE SKIP LOCKED`
4. `TaskExecutor` invokes step method
5. `ResultReporter` updates `step_runs` and `task_queue`
6. engine checks dependent steps; if all prerequisites are complete, enqueue them
7. workflow marked completed when all steps are completed

## Definition of Done

Phase 1 is complete when all are true:
- at least one workflow can be discovered from Spring context
- DAG validation catches invalid configurations
- workflow runs and step runs are persisted in PostgreSQL
- worker executes steps in dependency order
- queue polling is safe for multiple worker instances
- failures are recorded with error details
- local demo can show start-to-finish run status

## Demo Scenario

Use `document-pipeline`:
- `extractText -> chunkText -> embedChunks`

Demo checklist:
1. start application with PostgreSQL
2. trigger `workflowEngine.start("document-pipeline")`
3. show queue/task transitions (`pending -> running -> completed`)
4. show `workflow_runs` and `step_runs` persisted records
5. show final workflow completion status
