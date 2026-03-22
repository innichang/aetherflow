package io.github.inni.aetherflow.execution;

import io.github.inni.aetherflow.config.AetherflowProperties;
import io.github.inni.aetherflow.persistence.entity.StepRunEntity;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.entity.WorkerEntity;
import io.github.inni.aetherflow.persistence.entity.WorkerHeartbeatEntity;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.WorkerHeartbeatRepository;
import io.github.inni.aetherflow.persistence.repository.WorkerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaskPoller {

	private static final Logger log = LoggerFactory.getLogger(TaskPoller.class);

	private final TaskQueueClaimService taskQueueClaimService;
	private final TaskExecutor taskExecutor;
	private final ResultReporter resultReporter;
	private final StepRunRepository stepRunRepository;
	private final WorkerRepository workerRepository;
	private final WorkerHeartbeatRepository workerHeartbeatRepository;
	private final AetherflowProperties props;
	private final String workerId;

	public TaskPoller(
		TaskQueueClaimService taskQueueClaimService,
		TaskExecutor taskExecutor,
		ResultReporter resultReporter,
		StepRunRepository stepRunRepository,
		WorkerRepository workerRepository,
		WorkerHeartbeatRepository workerHeartbeatRepository,
		AetherflowProperties props
	) {
		this.taskQueueClaimService = taskQueueClaimService;
		this.taskExecutor = taskExecutor;
		this.resultReporter = resultReporter;
		this.stepRunRepository = stepRunRepository;
		this.workerRepository = workerRepository;
		this.workerHeartbeatRepository = workerHeartbeatRepository;
		this.props = props;
		this.workerId = "worker-" + UUID.randomUUID();
	}

	@PostConstruct
	public void registerWorker() {
		String hostname;
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			hostname = "unknown";
		}
		OffsetDateTime now = OffsetDateTime.now();
		WorkerEntity worker = new WorkerEntity();
		worker.setId(workerId);
		worker.setHostname(hostname);
		worker.setStartedAt(now);
		worker.setLastHeartbeat(now);
		worker.setStatus("active");
		workerRepository.save(worker);
	}

	@Scheduled(fixedDelayString = "${aetherflow.worker.poll-interval-ms:250}")
	public void poll() {
		Optional<TaskQueueEntity> task = taskQueueClaimService.claimNextTask(workerId, props.queue().lockTimeoutSeconds());
		task.ifPresent(this::executeTask);
	}

	@Scheduled(fixedDelayString = "${aetherflow.worker.heartbeat-interval-ms:5000}")
	public void heartbeat() {
		OffsetDateTime now = OffsetDateTime.now();
		workerRepository.findById(workerId).ifPresent(worker -> {
			worker.setLastHeartbeat(now);
			workerRepository.save(worker);
		});
		WorkerHeartbeatEntity heartbeat = new WorkerHeartbeatEntity();
		heartbeat.setId(UUID.randomUUID());
		heartbeat.setWorkerId(workerId);
		heartbeat.setRecordedAt(now);
		workerHeartbeatRepository.save(heartbeat);
	}

	@PreDestroy
	public void deregisterWorker() {
		workerRepository.findById(workerId).ifPresent(worker -> {
			worker.setStatus("inactive");
			workerRepository.save(worker);
		});
	}

	@Transactional
	void executeTask(TaskQueueEntity task) {
		StepRunEntity stepRun = stepRunRepository.findById(task.getStepRunId()).orElseThrow();
		stepRun.setIdempotencyKey(stepRun.getWorkflowRunId() + ":" + stepRun.getStepName() + ":" + stepRun.getAttempt());
		stepRun.setStatus("running");
		stepRun.setWorkerId(workerId);
		stepRun.setStartedAt(OffsetDateTime.now());
		stepRunRepository.save(stepRun);

		MDC.put("workflow_run_id", task.getWorkflowRunId().toString());
		MDC.put("step_run_id", task.getStepRunId().toString());
		MDC.put("workflow_name", task.getWorkflowName());
		MDC.put("step_name", task.getStepName());
		MDC.put("worker_id", workerId);
		MDC.put("attempt", String.valueOf(stepRun.getAttempt()));
		try {
			log.info("event=step.started workflow_run_id={} step_run_id={} workflow_name={} step_name={} worker_id={} attempt={}",
				task.getWorkflowRunId(), task.getStepRunId(),
				task.getWorkflowName(), task.getStepName(),
				workerId, stepRun.getAttempt());
			ExecutionResult result = taskExecutor.execute(task);
			resultReporter.report(task, result);
		} finally {
			MDC.clear();
		}
	}
}
