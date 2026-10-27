package com.mccprogrammer.extpulse.auth;

import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A server with registration closed, as production runs: only the allow-listed owner can
 * create an account.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "extpulse.auth.registration-open=false",
		"extpulse.auth.registration-allow-list=owner@example.com" })
@AutoConfigureMockMvc
class ClosedRegistrationTests {

	private static final String PASSWORD = "correct horse battery";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void deleteAccounts() {
		this.jdbc.sql("DELETE FROM users").update();
	}

	@Test
	void strangersCannotRegister() throws Exception {
		register("someone@example.com").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Registration is closed on this server"));
	}

	@Test
	void theAllowListedOwnerCanRegisterWhateverTheCaseAndLogIn() throws Exception {
		register("Owner@Example.com").andExpect(status().isCreated());

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"owner@example.com\", \"password\": \"%s\"}".formatted(PASSWORD)))
			.andExpect(status().isOk());
	}

	private ResultActions register(String email) throws Exception {
		return this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)));
	}

}
