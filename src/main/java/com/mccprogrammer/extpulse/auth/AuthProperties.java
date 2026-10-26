package com.mccprogrammer.extpulse.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login token settings ({@code extpulse.auth.*} in application.yaml).
 *
 * @param jwtSecret the key that signs tokens; empty means a random key per startup
 * @param tokenValidity how long a token works after login
 */
@ConfigurationProperties("extpulse.auth")
record AuthProperties(String jwtSecret, Duration tokenValidity) {
}
