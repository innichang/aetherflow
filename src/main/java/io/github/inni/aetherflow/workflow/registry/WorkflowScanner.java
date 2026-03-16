package io.github.inni.aetherflow.workflow.registry;

import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import io.github.inni.aetherflow.workflow.model.StepDefinition;
import io.github.inni.aetherflow.workflow.model.WorkflowDefinition;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class WorkflowScanner {

	private final ApplicationContext applicationContext;

	public WorkflowScanner(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	public List<ScannedWorkflow> scan() {
		Map<String, Object> beans = applicationContext.getBeansWithAnnotation(AIWorkflow.class);
		List<ScannedWorkflow> scannedWorkflows = new ArrayList<>();

		for (Map.Entry<String, Object> beanEntry : beans.entrySet()) {
			Object bean = beanEntry.getValue();
			Class<?> targetClass = AopUtils.getTargetClass(bean);
			AIWorkflow workflowAnnotation = targetClass.getAnnotation(AIWorkflow.class);
			List<StepDefinition> steps = new ArrayList<>();
			Map<String, StepInvoker> invokersByStepName = new HashMap<>();

			for (Method method : targetClass.getDeclaredMethods()) {
				Step step = method.getAnnotation(Step.class);
				if (step == null) {
					continue;
				}

				String stepName = step.name().isBlank() ? method.getName() : step.name();
				StepDefinition stepDefinition = new StepDefinition(
					stepName,
					method.getName(),
					Arrays.stream(step.dependsOn()).toList(),
					step.retries(),
					step.timeoutSeconds() > 0 ? step.timeoutSeconds() : null,
					step.concurrency() > 0 ? step.concurrency() : null
				);
				steps.add(stepDefinition);
				invokersByStepName.put(stepName, new StepInvoker(bean, method));
			}

			WorkflowDefinition definition = new WorkflowDefinition(
				workflowAnnotation.value(),
				beanEntry.getKey(),
				targetClass.getName(),
				List.copyOf(steps)
			);
			scannedWorkflows.add(new ScannedWorkflow(definition, Map.copyOf(invokersByStepName)));
		}

		return List.copyOf(scannedWorkflows);
	}

	public record ScannedWorkflow(WorkflowDefinition definition, Map<String, StepInvoker> invokersByStepName) {
	}
}

