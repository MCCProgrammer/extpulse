package com.mccprogrammer.extpulse.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.mccprogrammer.extpulse.store.Store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The SQL behind the stats. Two rules hold in both queries:
 * <ul>
 * <li>A day without a snapshot (a failed collection) uses the listing's last known
 * number, so totals don't dip when one store couldn't be read.</li>
 * <li>Gains are counted per listing and then added up. A listing added later has no
 * gain on its first day, instead of its whole install count showing as one day's gain.</li>
 * </ul>
 */
@Repository
class StatsQueries {

	private final JdbcClient jdbc;

	StatsQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Every listing of the user's extensions, with the latest snapshot on or before
	 * {@code day} and the installs on or before {@code weekBefore}. Extensions without
	 * listings come back once, with the listing columns empty.
	 */
	List<ListingRow> listingsOf(long userId, LocalDate day, LocalDate weekBefore) {
		return this.jdbc.sql("""
				SELECT e.id AS extension_id, e.display_name, p.name AS publisher,
				       l.id AS listing_id, l.store, l.external_id, l.url,
				       latest.taken_on, latest.installs, latest.rating_avg, latest.rating_count, latest.version,
				       (SELECT s.installs FROM snapshots s
				        WHERE s.listing_id = l.id AND s.taken_on <= :weekBefore
				        ORDER BY s.taken_on DESC LIMIT 1) AS installs_week_before
				FROM extensions e
				JOIN publishers p ON p.id = e.publisher_id
				LEFT JOIN listings l ON l.extension_id = e.id
				LEFT JOIN LATERAL (
				    SELECT s.taken_on, s.installs, s.rating_avg, s.rating_count, s.version
				    FROM snapshots s
				    WHERE s.listing_id = l.id AND s.taken_on <= :day
				    ORDER BY s.taken_on DESC
				    LIMIT 1
				) latest ON true
				WHERE p.user_id = :user
				ORDER BY e.id, l.id
				""")
				.param("user", userId)
				.param("day", day)
				.param("weekBefore", weekBefore)
				.query(ListingRow.class)
				.list();
	}

	/**
	 * One row per day from {@code from} to {@code to}: the extension's installs (in
	 * total and per store) and the day's gain. Days before the first snapshot are left out.
	 */
	List<DayRow> dailySeries(long extensionId, LocalDate from, LocalDate to) {
		return this.jdbc.sql("""
				WITH days AS (
				    -- From the day before "from", so the first day has a gain too.
				    SELECT d::date AS day
				    FROM generate_series(CAST(:from AS timestamp) - interval '1 day', CAST(:to AS timestamp),
				                         interval '1 day') AS d
				),
				per_listing AS (
				    -- Each listing's last known installs on each day (served by the
				    -- (listing_id, taken_on) index of the unique constraint).
				    SELECT days.day, l.id AS listing_id, l.store, s.installs
				    FROM days
				    CROSS JOIN listings l
				    JOIN LATERAL (
				        SELECT installs FROM snapshots
				        WHERE listing_id = l.id AND taken_on <= days.day
				        ORDER BY taken_on DESC
				        LIMIT 1
				    ) s ON true
				    WHERE l.extension_id = :extension
				),
				with_gain AS (
				    SELECT day, store, installs,
				           installs - LAG(installs) OVER (PARTITION BY listing_id ORDER BY day) AS gain
				    FROM per_listing
				)
				SELECT day,
				       sum(installs) AS installs,
				       sum(installs) FILTER (WHERE store = 'VS_MARKETPLACE') AS vs_marketplace,
				       sum(installs) FILTER (WHERE store = 'OPEN_VSX') AS open_vsx,
				       sum(installs) FILTER (WHERE store = 'CHROME_WEB_STORE') AS chrome_web_store,
				       sum(gain) AS gain
				FROM with_gain
				WHERE day >= :from
				GROUP BY day
				ORDER BY day
				""")
				.param("extension", extensionId)
				.param("from", from)
				.param("to", to)
				.query(DayRow.class)
				.list();
	}

	record ListingRow(long extensionId, String displayName, String publisher, Long listingId, Store store,
			String externalId, String url, LocalDate takenOn, Long installs, BigDecimal ratingAvg, Integer ratingCount,
			String version, Long installsWeekBefore) {
	}

	record DayRow(LocalDate day, long installs, Long vsMarketplace, Long openVsx, Long chromeWebStore, Long gain) {
	}

}
