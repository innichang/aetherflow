package io.github.inni.aetherflow.metrics;

import io.github.inni.aetherflow.persistence.repository.TaskQueueRepository;
import io.github.inni.aetherflow.persistence.repository.WorkerRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Registers live DB-backed gauges for queue depth and worker count.
 *
 * <p>Each gauge supplies a lambda that queries the database directly; Micrometer
 * calls the supplier on every Prometheus scrape (typically every 15–30s), so the
 * values are always current without any polling loop.
 *
 * <p>Metrics exposed:
 * <ul>
 *   <li>{@code aetherflow.queue.depth{status="pending"}} — tasks waiting to run
 *   <li>{@code aetherflow.queue.depth{status="running"}} — tasks currently claimed by a worker
 *   <li>{@code aetherflow.workers.active} — workers whose status is "active"
 * </ul>
 */
@Component
public class AetherflowGaugeMetrics {

	public AetherflowGaugeMetrics(
		MeterRegistry registry,
		TaskQueueRepository taskQueueRepository,
		WorkerRepository workerRepository
	) {
		Gauge.builder("aetherflow.queue.depth", taskQueueRepository, repo -> repo.countByStatus("pending"))
			.description("Number of tasks waiting to be claimed")
			.tag("status", "pending")
			.register(registry);

		Gauge.builder("aetherflow.queue.depth", taskQueueRepository, repo -> repo.countByStatus("running"))
			.description("Number of tasks currently being executed")
			.tag("status", "running")
			.register(registry);

		Gauge.builder("aetherflow.workers.active", workerRepository, repo -> repo.countByStatus("active"))
			.description("Number of registered workers with active status")
			.register(registry);
	}
}
