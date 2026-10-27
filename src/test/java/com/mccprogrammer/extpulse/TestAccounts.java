package com.mccprogrammer.extpulse;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accounts for tests, made through the real register and login endpoints: a token for the
 * API, or a signed-in session for the website.
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

	/** Registers the account and signs in with the website's form. */
	public static MockHttpSession sessionFor(MockMvc mvc, String email) throws Exception {
		tokenFor(mvc, email);
		return signIn(mvc, email);
	}

	/** Registers the account, makes it an admin and signs in with the website's form. */
	public static MockHttpSession adminSessionFor(MockMvc mvc, JdbcClient jdbc, String email) throws Exception {
		tokenFor(mvc, email);
		jdbc.sql("UPDATE users SET role = 'ADMIN' WHERE email = :email").param("email", email).update();
		return signIn(mvc, email);
	}

	private static MockHttpSession signIn(MockMvc mvc, String email) throws Exception {
		return (MockHttpSession) mvc
			.perform(post("/login").param("email", email).param("password", PASSWORD).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/app"))
			.andReturn()
			.getRequest()
			.getSession(false);
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
