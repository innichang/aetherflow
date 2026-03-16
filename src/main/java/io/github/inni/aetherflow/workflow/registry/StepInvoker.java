package io.github.inni.aetherflow.workflow.registry;

import java.lang.reflect.Method;

public record StepInvoker(Object bean, Method method) {

	public void invoke() throws ReflectiveOperationException {
		method.setAccessible(true);
		method.invoke(bean);
	}
}

