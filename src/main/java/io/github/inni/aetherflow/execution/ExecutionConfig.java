package io.github.inni.aetherflow.execution;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutionConfig {

	@Bean
	public ExecutorService stepExecutorService() {
		return Executors.newCachedThreadPool();
	}
}
