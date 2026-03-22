package io.github.inni.aetherflow.workflow.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Step {

	String name() default "";

	String[] dependsOn() default {};

	int retries() default 0;

	String backoff() default "";

	int timeoutSeconds() default 0;

	int concurrency() default 0;
}

