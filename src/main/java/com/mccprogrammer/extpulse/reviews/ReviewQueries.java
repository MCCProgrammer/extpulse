package com.mccprogrammer.extpulse.reviews;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.mccprogrammer.extpulse.store.Store;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The inbox: reviews of the user's listings, newest first, with optional filters. A
 * filter left empty (null) matches everything.
 */
@Repository
class ReviewQueries {

	private final JdbcClient jdbc;

	ReviewQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<ReviewRow> inbox(long userId, Filter filter, int limit, int offset) {
		// count(*) OVER () gives the total before LIMIT in the same query.
		return this.jdbc.sql("""
				SELECT r.id, e.id AS extension_id, e.display_name AS extension_name, l.store, r.rating, r.text,
				       r.author, r.product_version, r.posted_at, r.reply, r.first_seen_at, r.read_at,
				       count(*) OVER () AS total
				FROM reviews r
				JOIN listings l ON l.id = r.listing_id
				JOIN extensions e ON e.id = l.extension_id
				JOIN publishers p ON p.id = e.publisher_id
				WHERE p.user_id = :user
				  AND (CAST(:unread AS boolean) IS NULL OR (r.read_at IS NULL) = CAST(:unread AS boolean))
				  AND (CAST(:unanswered AS boolean) IS NOT TRUE OR (l.store = 'VS_MARKETPLACE' AND r.reply IS NULL))
				  AND (CAST(:maxRating AS integer) IS NULL OR r.rating <= CAST(:maxRating AS integer))
				  AND (CAST(:store AS text) IS NULL OR l.store = CAST(:store AS text))
				  AND (CAST(:extension AS bigint) IS NULL OR e.id = CAST(:extension AS bigint))
				ORDER BY r.posted_at DESC, r.id DESC
				LIMIT :limit OFFSET :offset
				""")
				.param("user", userId)
				.param("unread", filter.unread(), Types.BOOLEAN)
				.param("unanswered", filter.unanswered(), Types.BOOLEAN)
				.param("maxRating", filter.maxRating(), Types.INTEGER)
				.param("store", (filter.store() != null) ? filter.store().name() : null, Types.VARCHAR)
				.param("extension", filter.extensionId(), Types.BIGINT)
				.param("limit", limit)
				.param("offset", offset)
				.query(ReviewRow.class)
				.list();
	}

	/** Marks a review of the user's as read or unread; false if the user has no such review. */
	boolean markRead(long userId, long reviewId, boolean read, Instant now) {
		return this.jdbc.sql("""
				UPDATE reviews SET read_at = CASE WHEN :read THEN coalesce(read_at, :now) END
				WHERE id = :review
				  AND listing_id IN (
				      SELECT l.id FROM listings l
				      JOIN extensions e ON e.id = l.extension_id
				      JOIN publishers p ON p.id = e.publisher_id
				      WHERE p.user_id = :user)
				""")
				.param("read", read)
				.param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
				.param("review", reviewId)
				.param("user", userId)
				.update() == 1;
	}

	record Filter(Boolean unread, Boolean unanswered, Integer maxRating, Store store, Long extensionId) {
	}

	record ReviewRow(long id, long extensionId, String extensionName, Store store, int rating, String text,
			String author, String productVersion, OffsetDateTime postedAt, String reply, OffsetDateTime firstSeenAt,
			OffsetDateTime readAt, long total) {
	}

}
