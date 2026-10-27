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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stats over snapshots written straight to the database: a gap in the collection, a
 * listing added late, and a day with fewer installs than the day before.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StatsApiTests {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Lisbon"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private String token;

	private long visualizer;

	private long weekly;

	@BeforeEach
	void createExtensionsWithHistory() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		this.token = TestAccounts.tokenFor(this.mvc, "miguel@example.com");
		long publisher = id("INSERT INTO publishers (user_id, name) VALUES (%d, 'MCCProgrammer') RETURNING id"
			.formatted(TestAccounts.idOf(this.jdbc, "miguel@example.com")));

		// Marketplace: daily, with no snapshot 6 days ago (a failed collection) and a
		// drop 3 days ago (uninstalls). Open VSX: added 3 days ago.
		this.visualizer = extension(publisher, "Debug Data Structures Visualizer");
		long marketplace = listing(this.visualizer, "VS_MARKETPLACE", "MCCProgrammer.visualizer");
		long openVsx = listing(this.visualizer, "OPEN_VSX", "MCCProgrammer/visualizer");
		snapshots(marketplace, 9, 1000, 8, 1010, 7, 1020, 5, 1040, 4, 1050, 3, 1045, 2, 1060, 1, 1070, 0, 1080);
		snapshots(openVsx, 3, 500, 2, 505, 1, 510, 0, 520);

		long markify = listing(extension(publisher, "Markify"), "VS_MARKETPLACE", "MCCProgrammer.markify");
		snapshots(markify, 7, 100, 0, 130);
		extension(publisher, "No listings yet");

		// Fixed dates for weeks: 2026-10-05 and 2026-10-12 are Mondays. +2 installs a day
		// from 100 on 10-04.
		this.weekly = extension(publisher, "Weekly");
		long weeklyListing = listing(this.weekly, "OPEN_VSX", "MCCProgrammer/weekly");
		this.jdbc.sql("""
				INSERT INTO snapshots (listing_id, taken_on, installs)
				SELECT :listing, d::date, 100 + 2 * (d::date - DATE '2026-10-04')
				FROM generate_series(DATE '2026-10-04', DATE '2026-10-18', interval '1 day') d""")
			.param("listing", weeklyListing)
			.update();
	}

	@Test
	void listsTheExtensionsWithInstallsAndTheWeeksGainLargestFirst() throws Exception {
		request("/api/extensions").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].displayName")
				.value(contains("Debug Data Structures Visualizer", "Markify", "Weekly", "No listings yet")))
			// 1080 + 520; the gain counts only the Marketplace, the one listing with a
			// snapshot from a week ago: 1080 - 1020.
			.andExpect(jsonPath("$[0].installs").value(1600))
			.andExpect(jsonPath("$[0].weekGain").value(60))
			.andExpect(jsonPath("$[0].listings[?(@.store == 'OPEN_VSX')].weekGain").value(contains(nullValue())))
			.andExpect(jsonPath("$[1].installs").value(130))
			.andExpect(jsonPath("$[1].weekGain").value(30))
			// Weekly's last snapshot is from 2026-10-18: still its number today.
			.andExpect(jsonPath("$[2].installs").value(128))
			.andExpect(jsonPath("$[3].installs").value(0))
			.andExpect(jsonPath("$[3].weekGain").value(nullValue()))
			.andExpect(jsonPath("$[3].listings").value(empty()));
	}

	@Test
	void dailyGainsCarryTheLastNumberOverGapsAndIgnoreALateListingsFirstDay() throws Exception {
		request("/api/extensions/%d/stats?from=%s&to=%s".formatted(this.visualizer, TODAY.minusDays(6), TODAY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.granularity").value("DAY"))
			.andExpect(jsonPath("$.points[*].date").value(contains(days(6, 5, 4, 3, 2, 1, 0))))
			// 6 days ago has no snapshot: the 1020 of the day before, no change.
			.andExpect(jsonPath("$.points[*].installs").value(contains(1020, 1040, 1050, 1545, 1565, 1580, 1600)))
			// 3 days ago: the Marketplace lost 5, and Open VSX's first 500 is not a gain.
			.andExpect(jsonPath("$.points[*].gain").value(contains(0, 20, 10, -5, 20, 15, 20)))
			.andExpect(jsonPath("$.points[0].byStore.VS_MARKETPLACE").value(1020))
			.andExpect(jsonPath("$.points[0].byStore.OPEN_VSX").doesNotExist())
			.andExpect(jsonPath("$.points[6].byStore.OPEN_VSX").value(520));
	}

	@Test
	void groupsByIsoWeek() throws Exception {
		request("/api/extensions/%d/stats?from=2026-10-05&to=2026-10-18&granularity=week".formatted(this.weekly))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.points[*].date").value(contains("2026-10-05", "2026-10-12")))
			.andExpect(jsonPath("$.points[*].installs").value(contains(114, 128)))
			.andExpect(jsonPath("$.points[*].gain").value(contains(14, 14)));
	}

	@Test
	void defaultsToTheLast30Days() throws Exception {
		request("/api/extensions/%d/stats".formatted(this.visualizer)).andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value(TODAY.minusDays(29).toString()))
			.andExpect(jsonPath("$.to").value(TODAY.toString()))
			// Days before the first snapshot are left out.
			.andExpect(jsonPath("$.points[0].date").value(TODAY.minusDays(9).toString()));
	}

	@Test
	void rejectsRangesThatMakeNoSense() throws Exception {
		request("/api/extensions/%d/stats?from=2026-10-10&to=2026-10-01".formatted(this.visualizer))
			.andExpect(status().isBadRequest());
		request("/api/extensions/%d/stats?from=2024-01-01&to=2026-01-01".formatted(this.visualizer))
			.andExpect(status().isBadRequest());
		request("/api/extensions/%d/stats?granularity=month".formatted(this.visualizer))
			.andExpect(status().isBadRequest());
	}

	@Test
	void anotherAccountsStatsLookMissing() throws Exception {
		String other = TestAccounts.tokenFor(this.mvc, "someone@example.com");

		this.mvc.perform(
				get("/api/extensions/%d/stats".formatted(this.visualizer)).header("Authorization", "Bearer " + other))
			.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/extensions").header("Authorization", "Bearer " + other))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").value(empty()));
	}

	private ResultActions request(String path) throws Exception {
		return this.mvc.perform(get(path).header("Authorization", "Bearer " + this.token));
	}

	private long extension(long publisher, String name) {
		return id("INSERT INTO extensions (publisher_id, display_name) VALUES (%d, '%s') RETURNING id"
			.formatted(publisher, name));
	}

	private long listing(long extension, String store, String externalId) {
		return id("INSERT INTO listings (extension_id, store, external_id) VALUES (%d, '%s', '%s') RETURNING id"
			.formatted(extension, store, externalId));
	}

	/** Pairs of (days ago, installs). */
	private void snapshots(long listing, int... daysAgoAndInstalls) {
		for (int i = 0; i < daysAgoAndInstalls.length; i += 2) {
			this.jdbc.sql("INSERT INTO snapshots (listing_id, taken_on, installs) VALUES (:listing, :day, :installs)")
				.param("listing", listing)
				.param("day", TODAY.minusDays(daysAgoAndInstalls[i]))
				.param("installs", daysAgoAndInstalls[i + 1])
				.update();
		}
	}

	private static String[] days(int... daysAgo) {
		String[] days = new String[daysAgo.length];
		for (int i = 0; i < daysAgo.length; i++) {
			days[i] = TODAY.minusDays(daysAgo[i]).toString();
		}
		return days;
	}

	private long id(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
