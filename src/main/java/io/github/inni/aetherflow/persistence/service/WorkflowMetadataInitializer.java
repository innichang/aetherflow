package io.github.inni.aetherflow.persistence.service;

import io.github.inni.aetherflow.workflow.registry.WorkflowRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkflowMetadataInitializer {

	private final WorkflowRegistry workflowRegistry;
	private final WorkflowMetadataSyncService workflowMetadataSyncService;

	public WorkflowMetadataInitializer(
		WorkflowRegistry workflowRegistry,
		WorkflowMetadataSyncService workflowMetadataSyncService
	) {
		this.workflowRegistry = workflowRegistry;
		this.workflowMetadataSyncService = workflowMetadataSyncService;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void syncOnStartup() {
		workflowRegistry.all().forEach(workflowMetadataSyncService::sync);
	}
}

