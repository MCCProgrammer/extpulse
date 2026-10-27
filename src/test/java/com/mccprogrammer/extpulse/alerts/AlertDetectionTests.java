package com.mccprogrammer.extpulse.alerts;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What each rule finds, over data written straight to the database, with the clock fixed
 * on Monday 2026-10-26, 07:00 UTC (07:00 in Lisbon).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "extpulse.collection.cron=-", "extpulse.alerts.delivery-cron=-",
		"extpulse.alerts.weekly-summary-cron=-" })
class AlertDetectionTests {

	private static final Instant NOW = Instant.parse("2026-10-26T07:00:00Z");

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private AlertRuleRepository rules;

	@Autowired
	private AlertDetector detector;

	@Autowired
	private AlertOutbox outbox;

	@Autowired
	private DiscordAlertSender discord;

	@Autowired
	private AlertProperties properties;

	private AlertService alerts;

	private long user;

	private long diffChecker;

	private long marketplace;

	@BeforeEach
	void createAListingTrackedSinceSeptember() {
		this.jdbc.sql("DELETE FROM users").update();
		this.jdbc.sql("DELETE FROM collection_runs").update();
		this.alerts = new AlertService(this.rules, this.detector, this.outbox, List.of(), this.discord, this.properties,
				this.jdbc, Clock.fixed(NOW, ZoneId.of("Europe/Lisbon")));
		this.user = id("INSERT INTO users (email, password_hash) VALUES ('miguel@example.com', 'x') RETURNING id");
		long publisher = id("INSERT INTO publishers (user_id, name) VALUES (%d, 'MCCProgrammer') RETURNING id"
			.formatted(this.user));
		this.diffChecker = id(
				"INSERT INTO extensions (publisher_id, display_name) VALUES (%d, 'Diff Checker') RETURNING id"
					.formatted(publisher));
		this.marketplace = listing(this.diffChecker, "VS_MARKETPLACE", "MCCProgrammer.diff-checker", "2026-09-01");
	}

	@Test
	void aNewLowRatingAlertsOnceAndOldOrHighOnesDoNot() {
		rule("LOW_RATING", null);
		review("1", 1, "Does not work for me", "2026-10-25T20:00:00Z", "2026-10-26T06:00:00Z");
		review("2", 5, "great", "2026-10-25T20:00:00Z", "2026-10-26T06:00:00Z");
		// Written before the listing was tracked: history, not news.
		review("3", 1, "old", "2026-08-01T10:00:00Z", "2026-10-26T06:00:00Z");
		// First seen five days ago: an earlier check already had its chance.
		review("4", 2, "meh", "2026-10-20T10:00:00Z", "2026-10-21T06:00:00Z");

		assertThat(this.alerts.evaluate()).isEqualTo(1);
		assertThat(this.alerts.evaluate()).isZero();

		assertThat(events()).singleElement().satisfies((event) -> {
			assertThat(event).containsEntry("title", "1★ review of Diff Checker on VS Code Marketplace");
			assertThat((String) event.get("message")).contains("\"Does not work for me\"", "on version 1.0.0");
		});
	}

	@Test
	void aRaisedThresholdAlertsOnThreeStars() {
		rule("LOW_RATING", 3);
		review("1", 3, "ok-ish", "2026-10-25T20:00:00Z", "2026-10-26T06:00:00Z");

		assertThat(this.alerts.evaluate()).isEqualTo(1);
	}

	@Test
	void aNewVersionAlertsWithTheOneBefore() {
		rule("NEW_VERSION", null);
		snapshot(this.marketplace, "2026-10-25", 1760, "0.3.0");
		snapshot(this.marketplace, "2026-10-26", 1764, "0.3.1");

		this.alerts.evaluate();

		assertThat(events()).singleElement()
			.satisfies(
					(event) -> assertThat(event).containsEntry("title", "Diff Checker 0.3.1 is on VS Code Marketplace")
						.containsEntry("message", "The version before was 0.3.0.")
						.containsEntry("dedup_key", "version:" + this.marketplace + ":0.3.1"));
	}

	@Test
	void theFirstSnapshotOrAnOldChangeIsNotANewVersion() {
		rule("NEW_VERSION", null);
		long other = listing(this.diffChecker, "OPEN_VSX", "MCCProgrammer/diff-checker", "2026-09-01");
		snapshot(other, "2026-10-26", 1450, "0.3.1");
		snapshot(this.marketplace, "2026-10-20", 1700, "0.3.0");
		snapshot(this.marketplace, "2026-10-21", 1710, "0.3.1");
		snapshot(this.marketplace, "2026-10-26", 1764, "0.3.1");

		assertThat(this.alerts.evaluate()).isZero();
	}

	@Test
	void aDaysGainOverTwiceTheUsualIsASpike() {
		rule("INSTALL_SPIKE", null);
		dailyFrom("2026-10-10", "2026-10-25", 1000, 4);
		// 1060 on the 25th; +60 today against about 4 a day.
		snapshot(this.marketplace, "2026-10-26", 1120, "0.3.1");

		this.alerts.evaluate();

		assertThat(events()).singleElement()
			.satisfies((event) -> assertThat(event)
				.containsEntry("title", "Diff Checker: +60 installs on VS Code Marketplace on 26 Oct")
				.containsEntry("message", "Usually about 4 a day over the two weeks before."));
	}

	@Test
	void aSmallGainOrShortHistoryIsNoSpike() {
		rule("INSTALL_SPIKE", null);
		dailyFrom("2026-10-10", "2026-10-25", 1000, 4);
		// +8: twice the usual, but under the minimum of 10.
		snapshot(this.marketplace, "2026-10-26", 1068, "0.3.1");
		long young = listing(this.diffChecker, "OPEN_VSX", "MCCProgrammer/diff-checker", "2026-10-20");
		snapshot(young, "2026-10-22", 100, null);
		snapshot(young, "2026-10-25", 101, null);
		snapshot(young, "2026-10-26", 500, null);

		assertThat(this.alerts.evaluate()).isZero();
	}

	@Test
	void aListingWithoutNewNumbersForTwoDaysAlertsWithTheLastError() {
		rule("COLLECTION_FAILING", null);
		snapshot(this.marketplace, "2026-10-24", 1760, "0.3.1");
		long run = id("INSERT INTO collection_runs (started_at, status) VALUES (now(), 'PARTIAL') RETURNING id");
		this.jdbc
			.sql("""
					INSERT INTO collection_run_errors (run_id, listing_id, store, external_id, message)
					VALUES (:run, :listing, 'VS_MARKETPLACE', 'MCCProgrammer.diff-checker', 'stats: VS_MARKETPLACE: 503')""")
			.param("run", run)
			.param("listing", this.marketplace)
			.update();
		long recent = listing(this.diffChecker, "OPEN_VSX", "MCCProgrammer/diff-checker", "2026-09-01");
		snapshot(recent, "2026-10-25", 1450, null);
		long neverCollected = listing(this.diffChecker, "CHROME_WEB_STORE", "a".repeat(32), "2026-10-23");

		this.alerts.evaluate();

		assertThat(events()).extracting((event) -> event.get("title"))
			.containsExactlyInAnyOrder("No new numbers for Diff Checker on VS Code Marketplace since 2026-10-24",
					"No new numbers for Diff Checker on Chrome Web Store since it was added");
		assertThat(events())
			.filteredOn((event) -> event.get("dedup_key").equals("failing:" + this.marketplace + ":2026-10-24"))
			.singleElement()
			.satisfies((event) -> assertThat((String) event.get("message"))
				.endsWith("Last error: stats: VS_MARKETPLACE: 503"));
		assertThat(neverCollected).isPositive();
	}

	@Test
	void theWeeklySummaryAddsUpLastWeekPerExtensionAndStore() {
		rule("WEEKLY_SUMMARY", null);
		long openVsx = listing(this.diffChecker, "OPEN_VSX", "MCCProgrammer/diff-checker", "2026-09-01");
		long markify = listing(id(
				"INSERT INTO extensions (publisher_id, display_name) SELECT publisher_id, 'Markify' FROM extensions WHERE id = %d RETURNING id"
					.formatted(this.diffChecker)),
				"OPEN_VSX", "MCCProgrammer/markify", "2026-09-01");
		snapshot(this.marketplace, "2026-10-18", 1700, null);
		snapshot(this.marketplace, "2026-10-25", 1764, null);
		snapshot(openVsx, "2026-10-18", 1400, null);
		snapshot(openVsx, "2026-10-25", 1450, null);
		snapshot(markify, "2026-10-18", 1000, null);
		snapshot(markify, "2026-10-25", 1061, null);
		review("1", 5, "nice", "2026-10-20T10:00:00Z", "2026-10-21T06:00:00Z");
		review("2", 1, "bad", "2026-10-22T10:00:00Z", "2026-10-23T06:00:00Z");
		// Today, so next week's.
		review("3", 1, "later", "2026-10-26T05:00:00Z", "2026-10-26T06:00:00Z");

		assertThat(this.alerts.weeklySummary()).isEqualTo(1);
		assertThat(this.alerts.weeklySummary()).isZero();

		assertThat(events()).singleElement().satisfies((event) -> {
			// +64 +50 +61 = +175, over 1700 + 1400 + 1000 at the start of the week.
			assertThat(event).containsEntry("title", "Week of 19 Oct–25 Oct: +175 installs (+4.3%)")
				.containsEntry("dedup_key", "summary:2026-10-19");
			assertThat(event.get("message")).isEqualTo("""
					Diff Checker: +114 (VS Code Marketplace +64, Open VSX +50), 3,214 in total
					Markify: +61, 1,061 in total

					2 new reviews, the lowest 1★.""");
		});
	}

	@Test
	void anotherAccountsListingsDoNotAlertMe() {
		rule("LOW_RATING", null);
		long other = id("INSERT INTO users (email, password_hash) VALUES ('someone@example.com', 'x') RETURNING id");
		long theirPublisher = id(
				"INSERT INTO publishers (user_id, name) VALUES (%d, 'Someone') RETURNING id".formatted(other));
		long theirExtension = id(
				"INSERT INTO extensions (publisher_id, display_name) VALUES (%d, 'Theirs') RETURNING id"
					.formatted(theirPublisher));
		long theirListing = listing(theirExtension, "OPEN_VSX", "Someone/theirs", "2026-09-01");
		this.jdbc.sql("""
				INSERT INTO reviews (listing_id, external_id, rating, author, posted_at, first_seen_at)
				VALUES (:listing, 'github:x', 1, 'x', now(), now())""").param("listing", theirListing).update();

		assertThat(this.alerts.evaluate()).isZero();
	}

	private void rule(String type, Integer threshold) {
		this.jdbc.sql("""
				INSERT INTO alert_rules (user_id, type, threshold, channel, target)
				VALUES (:user, :type, :threshold, 'DISCORD', 'https://discord.com/api/webhooks/1/token')""")
			.param("user", this.user)
			.param("type", type)
			.param("threshold", threshold, java.sql.Types.INTEGER)
			.update();
	}

	private long listing(long extension, String store, String externalId, String createdOn) {
		return id("""
				INSERT INTO listings (extension_id, store, external_id, created_at)
				VALUES (%d, '%s', '%s', TIMESTAMPTZ '%s 00:00:00Z') RETURNING id""".formatted(extension, store,
				externalId, createdOn));
	}

	private void snapshot(long listing, String day, long installs, String version) {
		this.jdbc.sql("""
				INSERT INTO snapshots (listing_id, taken_on, installs, version)
				VALUES (:listing, CAST(:day AS date), :installs, :version)""")
			.param("listing", listing)
			.param("day", day)
			.param("installs", installs)
			.param("version", version, java.sql.Types.VARCHAR)
			.update();
	}

	/** One snapshot a day on the Marketplace listing, growing by {@code perDay}. */
	private void dailyFrom(String first, String last, long installs, int perDay) {
		this.jdbc.sql("""
				INSERT INTO snapshots (listing_id, taken_on, installs)
				SELECT :listing, d::date, :installs + :perDay * (d::date - CAST(:first AS date))
				FROM generate_series(CAST(:first AS date), CAST(:last AS date), interval '1 day') d""")
			.param("listing", this.marketplace)
			.param("first", first)
			.param("last", last)
			.param("installs", installs)
			.param("perDay", perDay)
			.update();
	}

	private void review(String externalId, int rating, String text, String postedAt, String firstSeenAt) {
		this.jdbc
			.sql("""
					INSERT INTO reviews (listing_id, external_id, rating, text, author, product_version, posted_at, first_seen_at)
					VALUES (:listing, :externalId, :rating, :text, 'Reviewer', '1.0.0', CAST(:postedAt AS timestamptz),
					        CAST(:firstSeenAt AS timestamptz))""")
			.param("listing", this.marketplace)
			.param("externalId", externalId)
			.param("rating", rating)
			.param("text", text)
			.param("postedAt", postedAt)
			.param("firstSeenAt", firstSeenAt)
			.update();
	}

	private List<Map<String, Object>> events() {
		return this.jdbc.sql("SELECT dedup_key, title, message FROM alert_events ORDER BY id").query().listOfRows();
	}

	private long id(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
