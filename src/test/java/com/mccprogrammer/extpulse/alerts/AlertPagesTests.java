package com.mccprogrammer.extpulse.alerts;

import com.mccprogrammer.extpulse.TestAccounts;
import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AlertPagesTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private MockHttpSession session;

	@BeforeEach
	void signIn() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		this.session = TestAccounts.sessionFor(this.mvc, "miguel@example.com");
	}

	@Test
	void anAlertIsAddedListedAndDeleted() throws Exception {
		add("LOW_RATING", "EMAIL", "", "").andExpect(redirectedUrl("/app/alerts"))
			.andExpect(flash().attribute("notice", "Alert added."));
		add("WEEKLY_SUMMARY", "DISCORD", "https://discord.com/api/webhooks/1/secret-token", "")
			.andExpect(flash().attribute("notice", "Alert added."));

		this.mvc.perform(get("/app/alerts").session(this.session))
			.andExpect(content().string(allOf(containsString("A review with 2 ★ or less"),
					containsString("miguel@example.com"), containsString("https://discord.com/api/webhooks/1/****"))));

		long rule = this.jdbc.sql("SELECT id FROM alert_rules WHERE type = 'LOW_RATING'").query(Long.class).single();
		this.mvc.perform(post("/app/alerts/{id}/delete", rule).session(this.session).with(csrf()))
			.andExpect(flash().attribute("notice", "Alert deleted."));
	}

	@Test
	void refusedRulesShowWhy() throws Exception {
		add("NEW_VERSION", "DISCORD", "https://example.com/hook", "")
			.andExpect(flash().attribute("error", containsString("must be a Discord webhook URL")));
		add("", "EMAIL", "", "").andExpect(flash().attribute("error", "Choose what to watch and where to send it."));
		this.mvc.perform(post("/app/alerts/{id}/test", 999999).session(this.session).with(csrf()))
			.andExpect(flash().attribute("error", "You have no alert 999999"));
	}

	private org.springframework.test.web.servlet.ResultActions add(String type, String channel, String target,
			String threshold) throws Exception {
		return this.mvc.perform(post("/app/alerts").param("type", type)
			.param("channel", channel)
			.param("target", target)
			.param("threshold", threshold)
			.session(this.session)
			.with(csrf()));
	}

}
