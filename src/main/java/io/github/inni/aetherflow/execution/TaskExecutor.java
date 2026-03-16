package io.github.inni.aetherflow.execution;

import io.github.inni.aetherflow.engine.WorkflowRuntimeException;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.workflow.registry.RegisteredWorkflow;
import io.github.inni.aetherflow.workflow.registry.StepInvoker;
import io.github.inni.aetherflow.workflow.registry.WorkflowRegistry;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutor {

	private final WorkflowRegistry workflowRegistry;

	public TaskExecutor(WorkflowRegistry workflowRegistry) {
		this.workflowRegistry = workflowRegistry;
	}

	public ExecutionResult execute(TaskQueueEntity task) {
		long startedNanos = System.nanoTime();
		try {
			RegisteredWorkflow workflow = workflowRegistry.getRequired(task.getWorkflowName());
			StepInvoker stepInvoker = workflow.invokersByStepName().get(task.getStepName());
			if (stepInvoker == null) {
				throw new WorkflowRuntimeException(
					"Step invoker missing for '%s' in workflow '%s'".formatted(task.getStepName(), task.getWorkflowName())
				);
			}
			stepInvoker.invoke();
			long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
			return ExecutionResult.success(durationMs);
		} catch (Throwable throwable) {
			long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
			return ExecutionResult.failure(durationMs, throwable);
		}
	}
}

