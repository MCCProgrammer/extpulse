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
import org.springframework.core.annotation.Order;
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
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Two ways in, one per kind of client:
 * <ul>
 * <li><b>The API</b> ({@code /api/**}) is stateless: a client logs in once, gets a signed
 * token (JWT) and sends it as {@code Authorization: Bearer <token>} on every request. The
 * app signs and checks its own tokens, so there is no session and no external identity
 * server.</li>
 * <li><b>The website</b> (everything else) signs in with a form and keeps a session
 * cookie, with CSRF protection on every form. The dashboard ({@code /app/**}) needs that
 * sign-in; the home page, public pages and badges don't.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfiguration {

	static final String ISSUER = "extpulse";

	private static final int MIN_SECRET_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	// Only this site's own files: no inline scripts or styles, no other origins, and no
	// framing. Swagger UI is left out, since it builds its page with inline code.
	private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data:; "
			+ "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

	@Bean
	@Order(1)
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/api/**")
			.authorizeHttpRequests(
					(requests) -> requests.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
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

	@Bean
	@Order(2)
	SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
		RequestMatcher swaggerUi = PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**");
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers("/", "/login", "/register", "/p/**", "/css/**", "/favicon.svg", "/error")
			.permitAll()
			.requestMatchers("/actuator/health/**", "/actuator/info")
			.permitAll()
			// The API description and Swagger UI: public, like the API's own docs would
			// be.
			.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
			.permitAll()
			.requestMatchers("/app/admin/**")
			.hasRole("ADMIN")
			.anyRequest()
			.authenticated())
			.formLogin((form) -> form.loginPage("/login").usernameParameter("email").defaultSuccessUrl("/app"))
			.logout((logout) -> logout.logoutSuccessUrl("/login?signedOut"))
			.headers((headers) -> headers
				.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(swaggerUi),
						new ContentSecurityPolicyHeaderWriter(CONTENT_SECURITY_POLICY))));
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
