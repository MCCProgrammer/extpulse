package com.mccprogrammer.extpulse.stats;

import java.time.LocalDate;
import java.time.ZoneId;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DashboardPagesTests {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Lisbon"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private MockHttpSession session;

	private long extension;

	@BeforeEach
	void anExtensionWithTenDays() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		this.session = TestAccounts.sessionFor(this.mvc, "miguel@example.com");
		long publisher = this.jdbc
			.sql("INSERT INTO publishers (user_id, name) VALUES (:user, 'MCCProgrammer') RETURNING id")
			.param("user", TestAccounts.idOf(this.jdbc, "miguel@example.com"))
			.query(Long.class)
			.single();
		this.extension = this.jdbc
			.sql("INSERT INTO extensions (publisher_id, display_name) VALUES (:publisher, 'Diff Checker') RETURNING id")
			.param("publisher", publisher)
			.query(Long.class)
			.single();
		long listing = this.jdbc.sql("""
				INSERT INTO listings (extension_id, store, external_id)
				VALUES (:extension, 'VS_MARKETPLACE', 'MCCProgrammer.diff-checker') RETURNING id""")
			.param("extension", this.extension)
			.query(Long.class)
			.single();
		// 1,000 ten days ago, then 10 more each day: 1,100 today.
		this.jdbc.sql("""
				INSERT INTO snapshots (listing_id, taken_on, installs)
				SELECT :listing, CAST(:today AS date) - n, 1100 - 10 * n FROM generate_series(0, 10) n""")
			.param("listing", listing)
			.param("today", TODAY)
			.update();
	}

	@Test
	void theOverviewListsTheExtensionsWithTheirTotals() throws Exception {
		this.mvc.perform(get("/app").session(this.session))
			.andExpect(status().isOk())
			.andExpect(model().attribute("installs", 1100L))
			.andExpect(model().attribute("weekGain", 70L))
			.andExpect(content().string(allOf(containsString("Diff Checker"), containsString("1,100"),
					containsString("+70"), containsString("VS Code Marketplace"))));
	}

	@Test
	void theExtensionPageDrawsBothCharts() throws Exception {
		this.mvc.perform(get("/app/extensions/{id}", this.extension).session(this.session))
			.andExpect(status().isOk())
			.andExpect(model().attribute("days", 30))
			.andExpect(content().string(allOf(containsString("class=\"line\""), containsString("class=\"column\""),
					containsString(": 1,100 installs (+10)"), containsString("Choose its address in Settings"))));
	}

	@Test
	void onlyTheOfferedRangesAreUsed() throws Exception {
		this.mvc.perform(get("/app/extensions/{id}", this.extension).param("days", "90").session(this.session))
			.andExpect(model().attribute("days", 90));
		this.mvc.perform(get("/app/extensions/{id}", this.extension).param("days", "5000").session(this.session))
			.andExpect(model().attribute("days", 30));
	}

	@Test
	void anotherAccountsExtensionIsNotFound() throws Exception {
		MockHttpSession other = TestAccounts.sessionFor(this.mvc, "other@example.com");

		this.mvc.perform(get("/app/extensions/{id}", this.extension).session(other)).andExpect(status().isNotFound());
	}

	@Test
	void anAccountWithNothingTrackedIsPointedToTheCatalog() throws Exception {
		MockHttpSession other = TestAccounts.sessionFor(this.mvc, "other@example.com");

		this.mvc.perform(get("/app").session(other))
			.andExpect(content().string(containsString("You don't track any extension yet")));
	}

}
