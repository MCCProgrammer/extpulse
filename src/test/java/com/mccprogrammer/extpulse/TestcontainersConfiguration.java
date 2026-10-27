package com.mccprogrammer.extpulse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// One container for the whole test run. Spring makes a context per distinct test
	// configuration, and each would otherwise start its own PostgreSQL. Sharing it means
	// a test can't assume an empty database: each one deletes what it relies on first.
	// Same version as compose.yaml, so tests run against the database used in
	// development.
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	// destroyMethod = "": the first context to close must not stop the container the
	// others still use. Testcontainers removes it when the test run ends.
	@Bean(destroyMethod = "")
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}

}
