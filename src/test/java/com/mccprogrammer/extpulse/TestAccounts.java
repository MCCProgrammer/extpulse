package com.mccprogrammer.extpulse;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accounts for API tests, made through the real register and login endpoints.
 */
public final class TestAccounts {

	public static final String PASSWORD = "correct horse battery";

	private TestAccounts() {
	}

	/** Registers the account and returns a login token for it. */
	public static String tokenFor(MockMvc mvc, String email) throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}""".formatted(email, PASSWORD))).andExpect(status().isCreated());
		return login(mvc, email);
	}

	/** Registers the account, makes it an admin and returns a login token for it. */
	public static String adminTokenFor(MockMvc mvc, JdbcClient jdbc, String email) throws Exception {
		tokenFor(mvc, email);
		jdbc.sql("UPDATE users SET role = 'ADMIN' WHERE email = :email").param("email", email).update();
		// A new login, so the token carries the new role.
		return login(mvc, email);
	}

	public static long idOf(JdbcClient jdbc, String email) {
		return jdbc.sql("SELECT id FROM users WHERE email = :email").param("email", email).query(Long.class).single();
	}

	private static String login(MockMvc mvc, String email) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.token");
	}

}
