package com.mccprogrammer.extpulse.collection;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import com.mccprogrammer.extpulse.catalog.ListingRepository;
import com.mccprogrammer.extpulse.reviews.ReviewWriter;
import com.mccprogrammer.extpulse.store.ListingStats;
import com.mccprogrammer.extpulse.store.Store;
import com.mccprogrammer.extpulse.store.StoreException;
import com.mccprogrammer.extpulse.store.StoreReview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The collection against a real PostgreSQL, with fake stores. No test transaction:
 * the collection commits as it goes, and that is part of what is tested.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "extpulse.collection.cron=-")
class CollectionServiceTests {

	private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

	private static final Instant MORNING = Instant.parse("2026-10-26T06:00:00Z");

	@Autowired
	private ListingRepository listings;

	@Autowired
	private CollectionRunRepository runs;

	@Autowired
	private SnapshotWriter snapshots;

	@Autowired
	private ReviewWriter reviews;

	@Autowired
	private JdbcClient jdbc;

	private final FakeStoreSource marketplace = new FakeStoreSource(Store.VS_MARKETPLACE);

	private final FakeStoreSource openVsx = new FakeStoreSource(Store.OPEN_VSX);

	private long marketplaceListing;

	private long openVsxListing;

	@BeforeEach
	void createTwoListingsOfOneExtension() {
		this.jdbc.sql("DELETE FROM collection_runs").update();
		this.jdbc.sql("DELETE FROM users").update();
		long user = id("INSERT INTO users (email, password_hash) VALUES ('miguel@example.com', 'x') RETURNING id");
		long publisher = id("INSERT INTO publishers (user_id, name) VALUES (" + user + ", 'MCCProgrammer') RETURNING id");
		long extension = id("INSERT INTO extensions (publisher_id, display_name) VALUES (" + publisher
				+ ", 'Diff Checker') RETURNING id");
		this.marketplaceListing = id("INSERT INTO listings (extension_id, store, external_id) VALUES (" + extension
				+ ", 'VS_MARKETPLACE', 'MCCProgrammer.diff-checker') RETURNING id");
		this.openVsxListing = id("INSERT INTO listings (extension_id, store, external_id) VALUES (" + extension
				+ ", 'OPEN_VSX', 'MCCProgrammer/diff-checker') RETURNING id");
	}

	@Test
	void savesTodaysSnapshotOfEveryListing() {
		this.marketplace.answers(new ListingStats(1764, new BigDecimal("1.00"), 1, "0.3.0"));
		this.openVsx.answers(new ListingStats(1450, null, 0, "0.3.0"));

		CollectionRun run = collectAt(MORNING);

		assertThat(run.getStatus()).isEqualTo(CollectionRunStatus.SUCCEEDED);
		assertThat(run.getListingsSucceeded()).isEqualTo(2);
		assertThat(run.getStartedAt()).isEqualTo(MORNING);
		assertThat(run.getFinishedAt()).isEqualTo(MORNING);
		assertThat(snapshotsOf(this.marketplaceListing)).containsExactly(
				Map.of("taken_on", "2026-10-26", "installs", 1764L, "rating_avg", "1.00", "rating_count", 1,
						"version", "0.3.0"));
		assertThat(snapshotsOf(this.openVsxListing)).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("installs", 1450L).containsEntry("rating_avg", "none"));
	}

	@Test
	void runningTwiceOnTheSameDayUpdatesTheSnapshotInsteadOfAddingOne() {
		this.marketplace.answers(new ListingStats(1764, null, 0, "0.3.0"));
		this.openVsx.answers(new ListingStats(1450, null, 0, "0.3.0"));
		collectAt(MORNING);

		this.marketplace.answers(new ListingStats(1770, null, 0, "0.3.1"));
		CollectionRun second = collectAt(MORNING.plus(Duration.ofHours(4)));

		assertThat(second.getStatus()).isEqualTo(CollectionRunStatus.SUCCEEDED);
		assertThat(snapshotsOf(this.marketplaceListing)).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("installs", 1770L).containsEntry("version", "0.3.1"));
	}

	@Test
	void theNextDayAddsANewSnapshot() {
		this.marketplace.answers(new ListingStats(1764, null, 0, "0.3.0"));
		this.openVsx.answers(new ListingStats(1450, null, 0, "0.3.0"));
		collectAt(MORNING);
		collectAt(MORNING.plus(Duration.ofDays(1)));

		assertThat(snapshotsOf(this.marketplaceListing)).extracting(row -> row.get("taken_on"))
			.containsExactly("2026-10-26", "2026-10-27");
	}

	@Test
	void theDayIsTheDayInLisbon() {
		this.marketplace.answers(new ListingStats(1764, null, 0, "0.3.0"));
		this.openVsx.answers(new ListingStats(1450, null, 0, "0.3.0"));

		// 23:30 UTC on 15 July is already 00:30 on 16 July in Lisbon (summer time, UTC+1).
		collectAt(Instant.parse("2026-07-15T23:30:00Z"));

		assertThat(snapshotsOf(this.marketplaceListing)).extracting(row -> row.get("taken_on"))
			.containsExactly("2026-07-16");
	}

	@Test
	void aFailingStoreDoesNotStopTheOthers() {
		this.marketplace.fails(new StoreException(Store.VS_MARKETPLACE, "query failed"));
		this.openVsx.answers(new ListingStats(1450, null, 0, "0.3.0"));

		CollectionRun run = collectAt(MORNING);

		assertThat(run.getStatus()).isEqualTo(CollectionRunStatus.PARTIAL);
		assertThat(run.getListingsFailed()).isEqualTo(1);
		assertThat(snapshotsOf(this.openVsxListing)).hasSize(1);
		assertThat(snapshotsOf(this.marketplaceListing)).isEmpty();
		assertThat(this.jdbc.sql("SELECT listing_id, store, external_id, message FROM collection_run_errors WHERE run_id = :run")
			.param("run", run.getId())
			.query()
			.listOfRows()).containsExactly(Map.of("listing_id", this.marketplaceListing, "store", "VS_MARKETPLACE",
					"external_id", "MCCProgrammer.diff-checker", "message", "stats: VS_MARKETPLACE: query failed"));
	}

	@Test
	void whenEveryListingFailsTheRunFailed() {
		this.marketplace.fails(new StoreException(Store.VS_MARKETPLACE, "query failed"));
		this.openVsx.fails(new StoreException(Store.OPEN_VSX, "503"));

		CollectionRun run = collectAt(MORNING);

		assertThat(run.getStatus()).isEqualTo(CollectionRunStatus.FAILED);
		assertThat(this.runs.findById(run.getId())).get()
			.satisfies(saved -> assertThat(saved.getStatus()).isEqualTo(CollectionRunStatus.FAILED));
	}

	@Test
	void savesReviewsAndUpdatesThemInsteadOfAddingThem() {
		this.marketplace.answers(new ListingStats(1764, null, 0, "0.3.0")).reviews(review("hard to see", null));
		this.openVsx.answers(new ListingStats(1450, null, 0, "0.3.0"));
		collectAt(MORNING);
		String firstSeen = reviewColumn("first_seen_at");

		this.marketplace.reviews(review("hard to see", "Thanks, fixed in 0.2.0"));
		collectAt(MORNING.plus(Duration.ofDays(1)));

		assertThat(this.jdbc.sql("SELECT count(*) FROM reviews").query(Long.class).single()).isEqualTo(1);
		assertThat(reviewColumn("reply")).isEqualTo("Thanks, fixed in 0.2.0");
		assertThat(reviewColumn("first_seen_at")).isEqualTo(firstSeen);
	}

	@Test
	void anEditedReviewIsUnreadAgain() {
		this.marketplace.answers(new ListingStats(1764, null, 0, "0.3.0")).reviews(review("hard to see", null));
		this.openVsx.answers(new ListingStats(1450, null, 0, "0.3.0"));
		collectAt(MORNING);
		this.jdbc.sql("UPDATE reviews SET read_at = now()").update();

		collectAt(MORNING.plus(Duration.ofHours(1)));
		assertThat(reviewColumn("read_at")).isNotNull();

		this.marketplace.reviews(review("hard to see, and now it crashes", null));
		collectAt(MORNING.plus(Duration.ofHours(2)));
		assertThat(reviewColumn("read_at")).isNull();
	}

	@Test
	void whenReviewsFailTheDaysNumbersAreStillSaved() {
		this.marketplace.answers(new ListingStats(1764, null, 0, "0.3.0"))
			.reviewsFail(new StoreException(Store.VS_MARKETPLACE, "reviews 500"));
		this.openVsx.answers(new ListingStats(1450, null, 0, "0.3.0"));

		CollectionRun run = collectAt(MORNING);

		assertThat(run.getStatus()).isEqualTo(CollectionRunStatus.PARTIAL);
		assertThat(snapshotsOf(this.marketplaceListing)).hasSize(1);
		assertThat(run.getErrors()).singleElement()
			.satisfies((error) -> assertThat(error.message()).isEqualTo("reviews: VS_MARKETPLACE: reviews 500"));
	}

	private static StoreReview review(String text, String reply) {
		return new StoreReview("326941", 1, text, "Reviewer 1", "0.1.1", Instant.parse("2026-01-29T15:11:13Z"), reply);
	}

	/** The one review's column as text, or null. */
	private String reviewColumn(String column) {
		List<String> values = this.jdbc.sql("SELECT " + column + "::text FROM reviews")
			.query((row, number) -> row.getString(1))
			.list();
		assertThat(values).hasSize(1);
		return values.getFirst();
	}

	private CollectionRun collectAt(Instant now) {
		ResilientStoreCalls calls = new ResilientStoreCalls(
				new CollectionProperties(2, Duration.ofMillis(1), Duration.ofMillis(1)));
		CollectionService service = new CollectionService(this.listings, this.runs, this.snapshots, this.reviews, calls,
				List.of(this.marketplace, this.openVsx), Clock.fixed(now, LISBON));
		return service.collect();
	}

	private List<Map<String, Object>> snapshotsOf(long listing) {
		return this.jdbc.sql("""
				SELECT taken_on::text AS taken_on, installs, coalesce(rating_avg::text, 'none') AS rating_avg,
				       rating_count, version
				FROM snapshots WHERE listing_id = :listing ORDER BY taken_on""")
			.param("listing", listing)
			.query()
			.listOfRows();
	}

	private long id(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
