package com.mccprogrammer.extpulse.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;
import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {

	private static final String PASSWORD = "correct horse battery";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private JwtEncoder encoder;

	@Autowired
	private UserAccountRepository accounts;

	@BeforeEach
	void deleteAccounts() {
		this.jdbc.sql("DELETE FROM users").update();
	}

	@Test
	void registersAnAccountWithALowercaseEmail() throws Exception {
		register("Miguel@Example.com", PASSWORD).andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("miguel@example.com"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void storesABcryptHashNeverThePassword() throws Exception {
		register("miguel@example.com", PASSWORD);

		String hash = this.jdbc.sql("SELECT password_hash FROM users").query(String.class).single();
		assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
	}

	@Test
	void theSameEmailWithDifferentCaseIsAConflict() throws Exception {
		register("miguel@example.com", PASSWORD);

		register("MIGUEL@example.com", "another password").andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("An account with this email already exists"));
	}

	@Test
	void saysWhichFieldsAreInvalid() throws Exception {
		register("not-an-email", "short").andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void rejectsAPasswordBcryptWouldCut() throws Exception {
		// 30 characters, but 90 bytes in UTF-8: BCrypt would only read the first 72.
		register("miguel@example.com", "€".repeat(30)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("72 bytes")));
	}

	@Test
	void logsInAndUsesTheTokenOnProtectedEndpoints() throws Exception {
		register("miguel@example.com", PASSWORD);

		String token = login("Miguel@example.com", PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();

		this.mvc.perform(get("/api/me").header("Authorization", "Bearer " + JsonPath.read(token, "$.token")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("miguel@example.com"));
	}

	@Test
	void aWrongPasswordAndAnUnknownEmailGetTheSameAnswer() throws Exception {
		register("miguel@example.com", PASSWORD);

		String wrongPassword = login("miguel@example.com", "not the password").andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String unknownEmail = login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(unknownEmail).isEqualTo(wrongPassword);
	}

	@Test
	void protectedEndpointsNeedAToken() throws Exception {
		this.mvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
	}

	@Test
	void rejectsATamperedToken() throws Exception {
		String token = tokenFor(registeredAccount(), Clock.systemUTC());
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

		this.mvc.perform(get("/api/me").header("Authorization", "Bearer " + tampered))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsAnExpiredToken() throws Exception {
		// Issued two hours ago with a one-hour validity: past the minute of allowed clock
		// skew.
		Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);

		this.mvc.perform(get("/api/me").header("Authorization", "Bearer " + tokenFor(registeredAccount(), twoHoursAgo)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsATokenSignedWithAnotherKey() throws Exception {
		JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
				"a different key, also 32+ bytes long".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		TokenService forger = new TokenService(otherKey, new AuthProperties("", Duration.ofHours(1), true, List.of()),
				Clock.systemUTC());

		this.mvc.perform(get("/api/me").header("Authorization", "Bearer " + forger.issue(registeredAccount()).value()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void adminPathsNeedTheAdminRole() throws Exception {
		UserAccount account = registeredAccount();
		String userToken = tokenFor(account, Clock.systemUTC());
		this.jdbc.sql("UPDATE users SET role = 'ADMIN' WHERE id = :id").param("id", account.getId()).update();
		String adminToken = tokenFor(this.accounts.findById(account.getId()).orElseThrow(), Clock.systemUTC());

		this.mvc.perform(post("/api/admin/anything").header("Authorization", "Bearer " + userToken))
			.andExpect(status().isForbidden());
		// Past security; there is just no admin endpoint there yet.
		this.mvc.perform(post("/api/admin/anything").header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isNotFound());
	}

	private ResultActions register(String email, String password) throws Exception {
		return this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}""".formatted(email, password)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return this.mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}""".formatted(email, password)));
	}

	private UserAccount registeredAccount() throws Exception {
		register("miguel@example.com", PASSWORD).andExpect(status().isCreated());
		return this.accounts.findByEmail("miguel@example.com").orElseThrow();
	}

	private String tokenFor(UserAccount account, Clock clock) {
		return new TokenService(this.encoder, new AuthProperties("", Duration.ofHours(1), true, List.of()), clock)
			.issue(account)
			.value();
	}

}
