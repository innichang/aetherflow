# Phase 5 - AI-Native Features (Differentiator)

## Goal

Introduce AI-first capabilities so AetherFlow is not only a generic workflow engine, but a platform optimized for LLM and ML pipelines.

This is the product differentiation phase.

## Milestone Outcome

AetherFlow can route model calls intelligently, track AI cost/latency, version prompts for reproducibility, and enforce output guardrails before downstream execution.

## Scope

### 1) Model Routing

Allow AI steps to declare model intent:

```java
@LLMStep(model = "gpt-4")
```

Routing capabilities:
- map logical model name to provider-specific model IDs
- choose model tier by policy (cheap, fast, high-quality, GPU-backed)
- fallback routing on provider/model errors

Required components:
- `ModelRouter`
- `ModelPolicyResolver`
- `ProviderAdapter` (OpenAI/Anthropic/local GPU, etc.)

### 2) Token Usage and Cost Tracking

Track AI invocation usage for operational and financial visibility.

Required data points per AI step call:
- prompt tokens
- completion tokens
- total tokens
- request latency
- estimated/actual cost
- provider and model

Persistence recommendation:
- `ai_step_usage` table keyed by `step_run_id`
- aggregated daily/monthly usage rollups for reporting

### 3) Prompt Versioning and Reproducibility

Persist prompt and model metadata used in each run.

Required metadata:
- `prompt_version`
- `prompt_template_id` (or hash)
- `model_version`
- `temperature`
- inference parameters (top_p, max_tokens, etc.)

Behavior goals:
- reproducible reruns with same prompt/model config
- traceability of behavior changes across prompt revisions

### 4) AI Output Validation and Guardrails

Validate AI outputs before allowing downstream steps.

Required checks:
- JSON/schema validation
- required fields enforcement
- content safety policies
- optional custom validation hooks

Runtime behavior:
- validation failures should mark step as failed/retryable based on policy
- invalid output should never silently propagate

## AI Execution Contract

Define a standard contract for AI step execution:
1. resolve prompt + model policy
2. invoke selected provider adapter
3. capture usage/cost/latency
4. validate output against schema/guardrails
5. persist normalized result and metadata

Recommended abstractions:
- `PromptResolver`
- `AIInvocationService`
- `OutputValidator`
- `UsageReporter`

## Definition of Done

Phase 5 is complete when all are true:
- AI steps can run through a model routing layer
- token/cost/latency are captured and queryable per step run
- prompt and model version metadata are persisted for reproducibility
- output validation blocks invalid responses from downstream tasks
- at least one end-to-end AI pipeline demonstrates these capabilities

## Demo Scenario

1. run a workflow with multiple `@LLMStep` tasks
2. show policy-based model selection (e.g., cheap vs premium route)
3. inspect per-step token usage and cost records
4. compare two runs using different prompt versions
5. trigger schema violation and show guardrail failure handling
