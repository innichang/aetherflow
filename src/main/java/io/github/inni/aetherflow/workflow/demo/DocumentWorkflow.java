package io.github.inni.aetherflow.workflow.demo;

import io.github.inni.aetherflow.workflow.annotation.AIWorkflow;
import io.github.inni.aetherflow.workflow.annotation.Step;
import org.springframework.stereotype.Component;

@Component
@AIWorkflow("document-pipeline")
public class DocumentWorkflow {

	@Step
	public void extractText() {
	}

	@Step(dependsOn = {"extractText"})
	public void chunkText() {
	}

	@Step(dependsOn = {"chunkText"})
	public void embedChunks() {
	}
}

