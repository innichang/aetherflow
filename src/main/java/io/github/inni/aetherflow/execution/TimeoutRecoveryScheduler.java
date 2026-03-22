package io.github.inni.aetherflow.execution;

import io.github.inni.aetherflow.config.AetherflowProperties;
import io.github.inni.aetherflow.persistence.entity.StepRunEntity;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.entity.WorkerEntity;
import io.github.inni.aetherflow.persistence.repository.StepRunRepository;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import io.github.inni.aetherflow.persistence.repository.WorkerRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TimeoutRecoveryScheduler {

	private static final Logger log = LoggerFactory.getLogger(TimeoutRecoveryScheduler.class);

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final TaskQueueRepository taskQueueRepository;
	private final StepRunRepository stepRunRepository;
	private final ResultReporter resultReporter;
	private final WorkerRepository workerRepository;
	private final AetherflowProperties props;

	public TimeoutRecoveryScheduler(
		NamedParameterJdbcTemplate jdbcTemplate,
		TaskQueueRepository taskQueueRepository,
		StepRunRepository stepRunRepository,
		ResultReporter resultReporter,
		WorkerRepository workerRepository,
		AetherflowProperties props
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.taskQueueRepository = taskQueueRepository;
		this.stepRunRepository = stepRunRepository;
		this.resultReporter = resultReporter;
		this.workerRepository = workerRepository;
		this.props = props;
	}

	@Scheduled(fixedDelayString = "${aetherflow.worker.recovery-interval-ms:10000}")
	public void recoverExpiredTasks() {
		List<UUID> expiredTaskIds = jdbcTemplate.query(
			"""
				SELECT id
				FROM task_queue
				WHERE status = 'running'
				  AND lock_expires_at < NOW()
				""",
			Map.of(),
			(rs, rowNum) -> (UUID) rs.getObject("id")
		);

		for (UUID taskId : expiredTaskIds) {
			try {
				recoverTask(taskId);
			} catch (Exception e) {
				log.warn("Failed to recover expired task {}: {}", taskId, e.getMessage(), e);
			}
		}
	}

	@Scheduled(fixedDelayString = "${aetherflow.worker.liveness-check-interval-ms:15000}")
	public void recoverDeadWorkers() {
		OffsetDateTime threshold = OffsetDateTime.now().minusNanos(props.worker().livenessTimeoutMs() * 1_000_000L);
		List<WorkerEntity> deadWorkers = workerRepository.findByStatusAndLastHeartbeatBefore("active", threshold);

		for (WorkerEntity worker : deadWorkers) {
			try {
				worker.setStatus("dead");
				workerRepository.save(worker);

				List<UUID> orphanedTaskIds = jdbcTemplate.query(
					"""
						SELECT id
						FROM task_queue
						WHERE status = 'running'
						  AND worker_id = :workerId
						""",
					Map.of("workerId", worker.getId()),
					(rs, rowNum) -> (UUID) rs.getObject("id")
				);

				for (UUID taskId : orphanedTaskIds) {
					try {
						recoverTask(taskId);
					} catch (Exception e) {
						log.warn("Failed to recover orphaned task {} from dead worker {}: {}", taskId, worker.getId(), e.getMessage(), e);
					}
				}
			} catch (Exception e) {
				log.warn("Failed to process dead worker {}: {}", worker.getId(), e.getMessage(), e);
			}
		}
	}

	private void recoverTask(UUID taskId) {
		TaskQueueEntity task = taskQueueRepository.findById(taskId).orElse(null);
		if (task == null) {
			return;
		}

		StepRunEntity stepRun = stepRunRepository.findById(task.getStepRunId()).orElse(null);
		if (stepRun == null) {
			return;
		}

		String message;
		if (stepRun.getRetryCount() < stepRun.getMaxRetries()) {
			message = "Task lock expired — worker may have crashed";
		} else {
			message = "Task lock expired after exhausting retries";
		}

		resultReporter.report(task, ExecutionResult.failure(0, new StepTimeoutException(message)));
	}
}
