package com.mccprogrammer.extpulse.collection;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * When the collection last worked, as a metric: the alert "no collection for a day" is
 * the current time minus this. Read from the database at each scrape, so a restart
 * doesn't reset it.
 */
@Component
class CollectionMetrics {

	private final JdbcClient jdbc;

	CollectionMetrics(JdbcClient jdbc, MeterRegistry meters) {
		this.jdbc = jdbc;
		Gauge.builder("extpulse.collection.last.success", this, CollectionMetrics::lastSuccess)
			.description("When the last collection that read at least one listing finished, in Unix time")
			.baseUnit("seconds")
			.register(meters);
	}

	/** Unix time in seconds, or NaN (no value) while no run has worked. */
	double lastSuccess() {
		// max() of no rows is one row holding NULL.
		Double seconds = this.jdbc.sql("""
				SELECT CAST(extract(epoch FROM max(finished_at)) AS float8) FROM collection_runs
				WHERE status IN ('SUCCEEDED', 'PARTIAL')""").query(Double.class).list().getFirst();
		return (seconds != null) ? seconds : Double.NaN;
	}

}
