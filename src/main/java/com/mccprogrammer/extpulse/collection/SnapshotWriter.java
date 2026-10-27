package com.mccprogrammer.extpulse.collection;

import java.sql.Types;
import java.time.LocalDate;

import com.mccprogrammer.extpulse.store.ListingStats;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Writes the day's snapshot of a listing. Plain SQL rather than JPA, because the point is
 * the upsert: a second write for the same listing and day replaces the numbers instead of
 * adding a row (or failing on the unique constraint).
 */
@Repository
class SnapshotWriter {

	private final JdbcClient jdbc;

	SnapshotWriter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void save(long listingId, LocalDate takenOn, ListingStats stats) {
		this.jdbc.sql("""
				INSERT INTO snapshots (listing_id, taken_on, installs, rating_avg, rating_count, version)
				VALUES (:listing, :takenOn, :installs, :ratingAvg, :ratingCount, :version)
				ON CONFLICT (listing_id, taken_on) DO UPDATE SET
				    installs = EXCLUDED.installs,
				    rating_avg = EXCLUDED.rating_avg,
				    rating_count = EXCLUDED.rating_count,
				    version = EXCLUDED.version,
				    collected_at = now()
				""")
			.param("listing", listingId)
			.param("takenOn", takenOn)
			.param("installs", stats.installs())
			.param("ratingAvg", stats.ratingAvg(), Types.NUMERIC)
			.param("ratingCount", stats.ratingCount())
			.param("version", stats.version(), Types.VARCHAR)
			.update();
	}

}
