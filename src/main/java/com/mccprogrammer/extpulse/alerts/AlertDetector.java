package com.mccprogrammer.extpulse.alerts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.mccprogrammer.extpulse.store.Store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Finds what each rule should alert about, in SQL, and words it. Every detection carries
 * a key naming what it is about (this review, this version, this day's spike), so finding
 * it again later adds nothing: the outbox keeps one alert per rule and key.
 */
@Component
class AlertDetector {

	// The listings each rule watches: those of the rule's user.
	private static final String RULE_LISTINGS = """
			FROM alert_rules ar
			JOIN publishers p ON p.user_id = ar.user_id
			JOIN extensions e ON e.publisher_id = p.id
			JOIN listings l ON l.extension_id = e.id
			""";

	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

	private final JdbcClient jdbc;

	AlertDetector(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Reviews first seen since {@code since} with few stars. Reviews written before the
	 * listing was tracked are history, not news, and don't alert.
	 */
	List<Detection> lowRatings(Instant since) {
		return this.jdbc.sql("""
				SELECT ar.id AS rule_id, r.id AS review_id, e.display_name, l.store, r.rating, r.text, r.author,
				       r.product_version
				""" + RULE_LISTINGS + """
				JOIN reviews r ON r.listing_id = l.id
				WHERE ar.type = 'LOW_RATING'
				  AND r.rating <= coalesce(ar.threshold, 2)
				  AND r.first_seen_at >= :since
				  AND r.posted_at >= l.created_at - interval '1 day'
				ORDER BY r.id
				""")
			.param("since", utc(since))
			.query(LowRating.class)
			.list()
			.stream()
			.map((row) -> new Detection(row.ruleId(), "review:" + row.reviewId(), new AlertMessage(
					"%s★ review of %s on %s".formatted(row.rating(), row.displayName(), row.store().displayName()),
					"%s\n\n%s%s".formatted((row.text() != null) ? "\"" + row.text() + "\"" : "(no comment)",
							"by " + row.author(),
							(row.productVersion() != null) ? ", on version " + row.productVersion() : ""))))
			.toList();
	}

	/**
	 * Listings whose latest snapshot, from today or yesterday, shows another version than
	 * the one before.
	 */
	List<Detection> newVersions(LocalDate today) {
		return this.jdbc
			.sql("""
					SELECT ar.id AS rule_id, l.id AS listing_id, e.display_name, l.store, cur.version, prev.version AS previous
					"""
					+ RULE_LISTINGS
					+ """
							JOIN LATERAL (SELECT taken_on, version FROM snapshots s WHERE s.listing_id = l.id
							              ORDER BY taken_on DESC LIMIT 1) cur ON true
							JOIN LATERAL (SELECT version FROM snapshots s WHERE s.listing_id = l.id AND s.taken_on < cur.taken_on
							              ORDER BY taken_on DESC LIMIT 1) prev ON true
							WHERE ar.type = 'NEW_VERSION'
							  AND cur.taken_on >= CAST(:today AS date) - 1
							  AND cur.version IS NOT NULL
							  AND cur.version IS DISTINCT FROM prev.version
							ORDER BY l.id
							""")
			.param("today", today)
			.query(NewVersion.class)
			.list()
			.stream()
			.map((row) -> new Detection(row.ruleId(), "version:" + row.listingId() + ":" + row.version(),
					new AlertMessage(
							"%s %s is on %s".formatted(row.displayName(), row.version(), row.store().displayName()),
							(row.previous() != null) ? "The version before was " + row.previous() + "."
									: "The first version ExtPulse sees with a number.")))
			.toList();
	}

	/**
	 * A day's gain on a listing of at least the rule's threshold (default 10) and more
	 * than twice its usual daily gain over the 14 days before. Needs two consecutive days
	 * and two weeks of history.
	 */
	List<Detection> installSpikes(LocalDate today) {
		return this.jdbc.sql("""
				SELECT ar.id AS rule_id, l.id AS listing_id, e.display_name, l.store, cur.taken_on,
				       cur.installs - prev.installs AS gain,
				       (prev.installs - base.installs)::numeric / (prev.taken_on - base.taken_on) AS usual
				""" + RULE_LISTINGS + """
				JOIN LATERAL (SELECT taken_on, installs FROM snapshots s WHERE s.listing_id = l.id
				              ORDER BY taken_on DESC LIMIT 1) cur ON true
				JOIN LATERAL (SELECT taken_on, installs FROM snapshots s WHERE s.listing_id = l.id
				              AND s.taken_on < cur.taken_on ORDER BY taken_on DESC LIMIT 1) prev ON true
				JOIN LATERAL (SELECT taken_on, installs FROM snapshots s WHERE s.listing_id = l.id
				              AND s.taken_on <= prev.taken_on - 14 ORDER BY taken_on DESC LIMIT 1) base ON true
				WHERE ar.type = 'INSTALL_SPIKE'
				  AND cur.taken_on >= CAST(:today AS date) - 1
				  AND cur.taken_on - prev.taken_on = 1
				  AND cur.installs - prev.installs >= coalesce(ar.threshold, 10)
				  AND cur.installs - prev.installs
				      > 2 * (prev.installs - base.installs)::numeric / (prev.taken_on - base.taken_on)
				ORDER BY l.id
				""")
			.param("today", today)
			.query(Spike.class)
			.list()
			.stream()
			.map((row) -> new Detection(row.ruleId(), "spike:" + row.listingId() + ":" + row
				.takenOn(), new AlertMessage(
						"%s: %s installs on %s on %s".formatted(row.displayName(), signed(row.gain()),
								row.store().displayName(), row.takenOn().format(DAY_MONTH)),
						"Usually about %s a day over the two weeks before.".formatted(
								row.usual().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()))))
			.toList();
	}

	/**
	 * Listings with no new snapshot for 2 days, or never collected 2 days after being
	 * added. One alert per outage: the key is the last day that was collected.
	 */
	List<Detection> failingListings(LocalDate today, Instant now) {
		return this.jdbc.sql("""
				SELECT ar.id AS rule_id, l.id AS listing_id, e.display_name, l.store, l.external_id,
				       last.taken_on AS last_collected_on,
				       (SELECT message FROM collection_run_errors err WHERE err.listing_id = l.id
				        ORDER BY err.run_id DESC LIMIT 1) AS last_error
				""" + RULE_LISTINGS + """
				LEFT JOIN LATERAL (SELECT taken_on FROM snapshots s WHERE s.listing_id = l.id
				                   ORDER BY taken_on DESC LIMIT 1) last ON true
				WHERE ar.type = 'COLLECTION_FAILING'
				  AND (last.taken_on <= CAST(:today AS date) - 2
				       OR (last.taken_on IS NULL AND l.created_at < CAST(:now AS timestamptz) - interval '2 days'))
				ORDER BY l.id
				""")
			.param("today", today)
			.param("now", utc(now))
			.query(Failing.class)
			.list()
			.stream()
			.map((row) -> new Detection(row.ruleId(),
					"failing:" + row.listingId() + ":"
							+ ((row.lastCollectedOn() != null) ? row.lastCollectedOn() : "never"),
					new AlertMessage(
							"No new numbers for %s on %s %s".formatted(row.displayName(), row.store().displayName(),
									(row.lastCollectedOn() != null) ? "since " + row.lastCollectedOn()
											: "since it was added"),
							"Listing %s.%s".formatted(row.externalId(),
									(row.lastError() != null) ? " Last error: " + row.lastError() : ""))))
			.toList();
	}

	/**
	 * One summary per weekly rule, about the ISO week that starts on {@code monday}: each
	 * extension's gain (in total and per store) and the reviews first seen that week.
	 */
	List<Detection> weeklySummaries(LocalDate monday, ZoneId zone) {
		LocalDate sunday = monday.plusDays(6);
		List<WeekRow> rows = this.jdbc.sql("""
				SELECT ar.id AS rule_id, e.id AS extension_id, e.display_name, l.store,
				       (SELECT installs FROM snapshots s WHERE s.listing_id = l.id AND s.taken_on <= :sunday
				        ORDER BY taken_on DESC LIMIT 1) AS installs_end,
				       (SELECT installs FROM snapshots s WHERE s.listing_id = l.id AND s.taken_on <= :weekBefore
				        ORDER BY taken_on DESC LIMIT 1) AS installs_start,
				       (SELECT count(*) FROM reviews r WHERE r.listing_id = l.id
				        AND r.first_seen_at >= :from AND r.first_seen_at < :to) AS new_reviews,
				       (SELECT min(rating) FROM reviews r WHERE r.listing_id = l.id
				        AND r.first_seen_at >= :from AND r.first_seen_at < :to) AS lowest_new_rating
				""" + RULE_LISTINGS + """
				WHERE ar.type = 'WEEKLY_SUMMARY'
				ORDER BY ar.id, e.id
				""")
			.param("sunday", sunday)
			.param("weekBefore", monday.minusDays(1))
			.param("from", monday.atStartOfDay(zone).toOffsetDateTime())
			.param("to", monday.plusWeeks(1).atStartOfDay(zone).toOffsetDateTime())
			.query(WeekRow.class)
			.list();
		Map<Long, List<WeekRow>> byRule = rows.stream()
			.collect(Collectors.groupingBy(WeekRow::ruleId, LinkedHashMap::new, Collectors.toList()));
		List<Detection> summaries = new ArrayList<>();
		byRule.forEach((rule, ruleRows) -> summaries
			.add(new Detection(rule, "summary:" + monday, summary(monday, sunday, ruleRows))));
		return summaries;
	}

	private static AlertMessage summary(LocalDate monday, LocalDate sunday, List<WeekRow> rows) {
		Map<Long, List<WeekRow>> byExtension = rows.stream()
			.collect(Collectors.groupingBy(WeekRow::extensionId, LinkedHashMap::new, Collectors.toList()));
		long gain = 0;
		long start = 0;
		List<ExtensionWeek> weeks = new ArrayList<>();
		for (List<WeekRow> listings : byExtension.values()) {
			ExtensionWeek week = ExtensionWeek.of(listings);
			weeks.add(week);
			gain += week.gain();
			start += week.startOfKnownGains();
		}
		weeks.sort(Comparator.comparingLong(ExtensionWeek::gain).reversed());
		String percent = (start > 0) ? " (%s%%)".formatted(signedDecimal(BigDecimal.valueOf(gain * 100.0 / start)))
				: "";
		StringBuilder body = new StringBuilder();
		for (ExtensionWeek week : weeks) {
			body.append(week.line()).append('\n');
		}
		long newReviews = rows.stream().mapToLong(WeekRow::newReviews).sum();
		Integer lowest = rows.stream()
			.map(WeekRow::lowestNewRating)
			.filter((rating) -> rating != null)
			.min(Integer::compare)
			.orElse(null);
		body.append('\n')
			.append((newReviews == 0) ? "No new reviews."
					: "%d new review%s, the lowest %d★.".formatted(newReviews, (newReviews == 1) ? "" : "s", lowest));
		return new AlertMessage("Week of %s–%s: %s installs%s".formatted(monday.format(DAY_MONTH),
				sunday.format(DAY_MONTH), signed(gain), percent), body.toString());
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	static String signed(long value) {
		return ((value >= 0) ? "+" : "") + NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value);
	}

	private static String signedDecimal(BigDecimal value) {
		BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP);
		return ((rounded.signum() >= 0) ? "+" : "") + rounded.toPlainString();
	}

	/** One thing to alert about: the rule, what it is about, and the words. */
	record Detection(long ruleId, String dedupKey, AlertMessage message) {
	}

	record LowRating(long ruleId, long reviewId, String displayName, Store store, int rating, String text,
			String author, String productVersion) {
	}

	record NewVersion(long ruleId, long listingId, String displayName, Store store, String version, String previous) {
	}

	record Spike(long ruleId, long listingId, String displayName, Store store, LocalDate takenOn, long gain,
			BigDecimal usual) {
	}

	record Failing(long ruleId, long listingId, String displayName, Store store, String externalId,
			LocalDate lastCollectedOn, String lastError) {
	}

	record WeekRow(long ruleId, long extensionId, String displayName, Store store, Long installsEnd, Long installsStart,
			long newReviews, Integer lowestNewRating) {
	}

	/**
	 * An extension's week, from its listings' rows. A listing without a start of week
	 * adds no gain.
	 */
	private record ExtensionWeek(String displayName, long installs, long gain, long startOfKnownGains,
			List<String> byStore) {

		static ExtensionWeek of(List<WeekRow> listings) {
			long installs = 0;
			long gain = 0;
			long start = 0;
			List<String> byStore = new ArrayList<>();
			// Store order (Marketplace first), as everywhere else; SQL sorts the names as
			// text.
			List<WeekRow> sorted = listings.stream().sorted(Comparator.comparing(WeekRow::store)).toList();
			for (WeekRow listing : sorted) {
				installs += (listing.installsEnd() != null) ? listing.installsEnd() : 0;
				if (listing.installsEnd() != null && listing.installsStart() != null) {
					long listingGain = listing.installsEnd() - listing.installsStart();
					gain += listingGain;
					start += listing.installsStart();
					byStore.add(listing.store().displayName() + " " + signed(listingGain));
				}
			}
			return new ExtensionWeek(listings.getFirst().displayName(), installs, gain, start, byStore);
		}

		String line() {
			String stores = (this.byStore.size() > 1) ? " (" + String.join(", ", this.byStore) + ")" : "";
			return "%s: %s%s, %s in total".formatted(this.displayName, signed(this.gain), stores,
					NumberFormat.getIntegerInstance(Locale.ENGLISH).format(this.installs));
		}

	}

}
