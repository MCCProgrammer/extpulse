package com.mccprogrammer.extpulse.auth;

import com.mccprogrammer.extpulse.TestAccounts;
import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The website signs in with a form and a session; the API keeps its tokens. Each must
 * answer as its own kind of client expects.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WebsiteSecurityTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void deleteAccounts() {
		this.jdbc.sql("DELETE FROM users").update();
	}

	@Test
	void theDashboardSendsStrangersToSignIn() throws Exception {
		this.mvc.perform(get("/app")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
	}

	@Test
	void theApiStillAnswers401InsteadOfRedirecting() throws Exception {
		this.mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void signingInOpensTheDashboardAndSigningOutClosesIt() throws Exception {
		MockHttpSession session = TestAccounts.sessionFor(this.mvc, "miguel@example.com");

		this.mvc.perform(get("/app").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Overview")));

		this.mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?signedOut"));
		this.mvc.perform(get("/app").session(session)).andExpect(status().isFound());
	}

	@Test
	void aWrongPasswordGoesBackToTheForm() throws Exception {
		TestAccounts.tokenFor(this.mvc, "miguel@example.com");

		this.mvc.perform(
				post("/login").param("email", "miguel@example.com").param("password", "wrong password").with(csrf()))
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void formsWithoutTheCsrfTokenAreRefused() throws Exception {
		TestAccounts.tokenFor(this.mvc, "miguel@example.com");

		this.mvc.perform(post("/login").param("email", "miguel@example.com").param("password", TestAccounts.PASSWORD))
			.andExpect(status().isForbidden());
	}

	@Test
	void pagesOnlyLoadThisSitesOwnFilesButSwaggerUiIsLeftAlone() throws Exception {
		this.mvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
			.andExpect(header().string("X-Frame-Options", "DENY"));
		this.mvc.perform(get("/swagger-ui/index.html")).andExpect(header().doesNotExist("Content-Security-Policy"));
	}

	@Test
	void theRegisterPageCreatesAnAccountThatCanSignIn() throws Exception {
		this.mvc
			.perform(post("/register").param("email", "New@Example.com")
				.param("password", TestAccounts.PASSWORD)
				.with(csrf()))
			.andExpect(redirectedUrl("/login"))
			.andExpect(flash().attribute("notice", "Account created. Sign in with it."));

		this.mvc.perform(
				post("/login").param("email", "new@example.com").param("password", TestAccounts.PASSWORD).with(csrf()))
			.andExpect(redirectedUrl("/app"));
	}

	@Test
	void theRegisterPageShowsWhatIsWrong() throws Exception {
		this.mvc.perform(post("/register").param("email", "not an email").param("password", "short").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("field-error")));

		TestAccounts.tokenFor(this.mvc, "taken@example.com");
		this.mvc
			.perform(post("/register").param("email", "taken@example.com")
				.param("password", TestAccounts.PASSWORD)
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("An account with this email already exists")));
	}

	@Test
	void theSignInPageOffersRegistrationOnlyWhileItIsOpen() throws Exception {
		this.mvc.perform(get("/login")).andExpect(content().string(containsString("/register")));
		this.mvc.perform(get("/register")).andExpect(content().string(not(containsString("Registration is closed"))));
	}

	@Test
	void errorsInTheBrowserArePages() throws Exception {
		// MockMvc doesn't forward errors to /error the way the server does, so this asks
		// for it directly.
		this.mvc
			.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404).accept(MediaType.TEXT_HTML))
			.andExpect(status().isNotFound())
			.andExpect(content().string(containsString("This page does not exist, or it is not public.")));
	}

}
