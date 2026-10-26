package com.mccprogrammer.extpulse;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for the whole app, in extpulse.zone. Code asks it for the time instead of
 * calling {@code Instant.now()}, so tests can fix the time.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfiguration {

	@Bean
	Clock clock(@Value("${extpulse.zone}") ZoneId zone) {
		return Clock.system(zone);
	}

}
