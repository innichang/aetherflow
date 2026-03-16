package io.github.inni.aetherflow.execution;

import io.github.inni.aetherflow.persistence.entity.StepRunEntity;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaskPoller {

	private final TaskQueueClaimService taskQueueClaimService;
	private final TaskExecutor taskExecutor;
	private final ResultReporter resultReporter;
	private final StepRunRepository stepRunRepository;
	private final String workerId;

	public TaskPoller(
		TaskQueueClaimService taskQueueClaimService,
		TaskExecutor taskExecutor,
		ResultReporter resultReporter,
		StepRunRepository stepRunRepository
	) {
		this.taskQueueClaimService = taskQueueClaimService;
		this.taskExecutor = taskExecutor;
		this.resultReporter = resultReporter;
		this.stepRunRepository = stepRunRepository;
		this.workerId = "worker-" + UUID.randomUUID();
	}

	@Scheduled(fixedDelayString = "${aetherflow.worker.poll-interval-ms:250}")
	public void poll() {
		int lockSeconds = ThreadLocalRandom.current().nextInt(20, 31);
		Optional<TaskQueueEntity> task = taskQueueClaimService.claimNextTask(workerId, lockSeconds);
		task.ifPresent(this::executeTask);
	}

	@Transactional
	void executeTask(TaskQueueEntity task) {
		StepRunEntity stepRun = stepRunRepository.findById(task.getStepRunId()).orElseThrow();
		stepRun.setStatus("running");
		stepRun.setWorkerId(workerId);
		stepRun.setStartedAt(OffsetDateTime.now());
		stepRunRepository.save(stepRun);

		ExecutionResult result = taskExecutor.execute(task);
		resultReporter.report(task, result);
	}
}

