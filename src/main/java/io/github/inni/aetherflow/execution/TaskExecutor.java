package io.github.inni.aetherflow.execution;

import io.github.inni.aetherflow.engine.WorkflowRuntimeException;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.workflow.model.StepDefinition;
import io.github.inni.aetherflow.workflow.registry.RegisteredWorkflow;
import io.github.inni.aetherflow.workflow.registry.StepInvoker;
import io.github.inni.aetherflow.workflow.registry.WorkflowRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutor {

	private final WorkflowRegistry workflowRegistry;
	private final ExecutorService stepExecutorService;

	public TaskExecutor(WorkflowRegistry workflowRegistry, ExecutorService stepExecutorService) {
		this.workflowRegistry = workflowRegistry;
		this.stepExecutorService = stepExecutorService;
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

			Integer timeoutSeconds = workflow.definition().steps().stream()
				.filter(s -> s.stepName().equals(task.getStepName()))
				.findFirst()
				.map(StepDefinition::timeoutSeconds)
				.orElse(null);

			if (timeoutSeconds == null || timeoutSeconds <= 0) {
				stepInvoker.invoke();
				long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
				return ExecutionResult.success(durationMs);
			}

			Future<?> future = stepExecutorService.submit(() -> {
				try {
					stepInvoker.invoke();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

			try {
				future.get(timeoutSeconds, TimeUnit.SECONDS);
				long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
				return ExecutionResult.success(durationMs);
			} catch (TimeoutException e) {
				future.cancel(true);
				long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
				return ExecutionResult.failure(durationMs, new StepTimeoutException(
					"Step '%s' in workflow '%s' timed out after %d seconds"
						.formatted(task.getStepName(), task.getWorkflowName(), timeoutSeconds)
				));
			} catch (java.util.concurrent.ExecutionException e) {
				long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				// unwrap the RuntimeException wrapper added in the submit lambda
				if (cause instanceof RuntimeException re && re.getCause() != null) {
					cause = re.getCause();
				}
				// unwrap InvocationTargetException to surface the actual step exception
				if (cause instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null) {
					cause = ite.getCause();
				}
				return ExecutionResult.failure(durationMs, cause);
			}
		} catch (Throwable throwable) {
			long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
			return ExecutionResult.failure(durationMs, throwable);
		}
	}
}
