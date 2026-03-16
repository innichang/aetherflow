package io.github.inni.aetherflow.execution;

import io.github.inni.aetherflow.engine.ExecutionStatus;
import io.github.inni.aetherflow.persistence.entity.TaskQueueEntity;
import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskQueueClaimService {

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final TaskQueueRepository taskQueueRepository;

	public TaskQueueClaimService(NamedParameterJdbcTemplate jdbcTemplate, TaskQueueRepository taskQueueRepository) {
		this.jdbcTemplate = jdbcTemplate;
		this.taskQueueRepository = taskQueueRepository;
	}

	@Transactional
	public Optional<TaskQueueEntity> claimNextTask(String workerId, int lockSeconds) {
		var params = Map.<String, Object>of("status", ExecutionStatus.PENDING);
		var taskIds = jdbcTemplate.query(
			"""
				SELECT id
				FROM task_queue
				WHERE status = :status
				  AND available_at <= NOW()
				ORDER BY priority ASC, available_at ASC
				FOR UPDATE SKIP LOCKED
				LIMIT 1
				""",
			params,
			(rs, rowNum) -> (UUID) rs.getObject("id")
		);

		if (taskIds.isEmpty()) {
			return Optional.empty();
		}

		UUID taskId = taskIds.get(0);
		TaskQueueEntity task = taskQueueRepository.findById(taskId).orElseThrow();
		OffsetDateTime now = OffsetDateTime.now();
		task.setStatus(ExecutionStatus.RUNNING);
		task.setWorkerId(workerId);
		task.setLockedAt(now);
		task.setLockExpiresAt(now.plusSeconds(lockSeconds));
		taskQueueRepository.save(task);
		return Optional.of(task);
	}
}

