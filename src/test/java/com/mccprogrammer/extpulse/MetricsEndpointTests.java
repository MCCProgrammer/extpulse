package com.mccprogrammer.extpulse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As production runs it: the actuator on its own port. Prometheus reads the metrics there
 * without logging in; the public port doesn't serve them at all. Tests turn metrics
 * export off by default; {@code @AutoConfigureMetrics} turns it back on.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class MetricsEndpointTests {

	@LocalServerPort
	private int port;

	@LocalManagementPort
	private int managementPort;

	@Autowired
	private JdbcClient jdbc;

	private final RestClient http = RestClient.builder()
		.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
		})
		.build();

	@BeforeEach
	void oneSuccessfulRun() {
		this.jdbc.sql("DELETE FROM collection_runs").update();
		this.jdbc.sql("""
				INSERT INTO collection_runs (started_at, finished_at, status, listings_total, listings_succeeded)
				VALUES (TIMESTAMPTZ '2026-10-27 06:00:00Z', TIMESTAMPTZ '2026-10-27 06:00:12Z', 'SUCCEEDED', 1, 1)""")
			.update();
	}

	@Test
	void prometheusReadsTheMetricsOnTheManagementPort() {
		String body = this.http.get()
			.uri("http://localhost:" + this.managementPort + "/actuator/prometheus")
			.retrieve()
			.body(String.class);

		assertThat(body).contains("jvm_memory_used_bytes")
			.contains("hikaricp_connections_active")
			.contains("extpulse_collection_last_success_seconds 1.793080812E9");
	}

	@Test
	void thePublicPortHasNoMetrics() {
		int status = this.http.get()
			.uri("http://localhost:" + this.port + "/actuator/prometheus")
			.retrieve()
			.toBodilessEntity()
			.getStatusCode()
			.value();

		assertThat(status).isEqualTo(404);
	}

}
