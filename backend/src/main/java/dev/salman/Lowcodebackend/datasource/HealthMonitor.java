package dev.salman.Lowcodebackend.datasource;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pings every enabled Data source and records the latest result. A failing source is shown as failing but is never
 * switched off. The sources are pinged in parallel on virtual threads so that a few unreachable hosts (each waiting
 * out its connect timeout) cannot push the whole round past the interval.
 */
@Component
class HealthMonitor {

	private static final Logger log = LoggerFactory.getLogger(HealthMonitor.class);

	private final HealthStore store;
	private final DataSourceService service;

	HealthMonitor(HealthStore store, DataSourceService service) {
		this.store = store;
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${tessle.datasource.health-interval:PT5M}",
			initialDelayString = "${tessle.datasource.health-interval:PT5M}")
	void pingAll() {
		List<DataSourceRecord> sources = store.enabled();
		try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
			List<? extends Future<?>> done = sources.stream().map(ds -> pool.submit(() -> ping(ds))).toList();
			for (Future<?> f : done) {
				try {
					f.get();
				}
				catch (Exception e) {
					log.warn("Data source ping failed to run", e);
				}
			}
		}
	}

	private void ping(DataSourceRecord ds) {
		ConnectionTester.Result result;
		try {
			result = service.probe(ds);
		}
		catch (RuntimeException e) {
			// E.g. a secret that cannot be opened: show it as failing rather than leaving a stale "healthy".
			log.warn("Data source {} could not be pinged", ds.id(), e);
			result = new ConnectionTester.Result(false, ConnectionTester.FAILED, "The check could not run.");
		}
		store.record(ds, result.ok(), result.ok() ? null : result.message());
	}

}
