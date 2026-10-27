package com.mccprogrammer.extpulse.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security: a client logs in once, gets a signed token (JWT) and sends it as
 * {@code Authorization: Bearer <token>} on every request. The app signs and checks its
 * own tokens, so there is no session and no external identity server.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfiguration {

	static final String ISSUER = "extpulse";

	private static final int MIN_SECRET_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(
				(requests) -> requests.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
					.permitAll()
					.requestMatchers("/actuator/health/**", "/actuator/info", "/error")
					.permitAll()
					// The API description and Swagger UI: public, like the API's own docs
					// would be.
					.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
					.permitAll()
					.requestMatchers("/api/admin/**")
					.hasAuthority("SCOPE_admin")
					.anyRequest()
					.authenticated())
			.oauth2ResourceServer((resourceServer) -> resourceServer.jwt(Customizer.withDefaults()))
			.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// CSRF abuses credentials the browser sends by itself (cookies). The token
			// travels in a header that only our client code sets, so there is nothing to
			// abuse.
			.csrf((csrf) -> csrf.disable());
		return http.build();
	}

	/** BCrypt today; the "{bcrypt}" prefix lets a future algorithm read old hashes. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	SecretKey jwtSigningKey(AuthProperties properties) {
		String secret = properties.jwtSecret();
		if (secret == null || secret.isBlank()) {
			logger.warn("extpulse.auth.jwt-secret is not set: using a random key, so login tokens stop working "
					+ "when the app restarts. Set EXTPULSE_AUTH_JWT_SECRET in production.");
			byte[] random = new byte[MIN_SECRET_BYTES];
			RANDOM.nextBytes(random);
			return new SecretKeySpec(random, "HmacSHA256");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"extpulse.auth.jwt-secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		// Signature, expiry (with a minute of clock skew) and that we issued it.
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

}
