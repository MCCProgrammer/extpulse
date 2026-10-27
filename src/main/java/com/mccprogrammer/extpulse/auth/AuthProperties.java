package com.mccprogrammer.extpulse.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Account and login settings ({@code extpulse.auth.*} in application.yaml).
 *
 * @param jwtSecret the key that signs tokens; empty means a random key per startup
 * @param tokenValidity how long a token works after login
 * @param registrationOpen whether anyone can create an account
 * @param registrationAllowList emails that can create an account while registration is
 * closed (the owner of a server, for example)
 */
@ConfigurationProperties("extpulse.auth")
record AuthProperties(String jwtSecret, Duration tokenValidity, boolean registrationOpen,
		List<String> registrationAllowList) {

	AuthProperties {
		registrationAllowList = (registrationAllowList != null) ? List.copyOf(registrationAllowList) : List.of();
	}

	boolean mayRegister(String normalizedEmail) {
		return this.registrationOpen
				|| this.registrationAllowList.stream().anyMatch((allowed) -> allowed.equalsIgnoreCase(normalizedEmail));
	}

}
