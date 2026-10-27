package com.mccprogrammer.extpulse.reviews;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.mccprogrammer.extpulse.store.StoreReview;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves the reviews a store shows for a listing. Upserted like the snapshots: a review
 * seen again is updated (edited text, a new reply), never duplicated, and keeps the day
 * ExtPulse first saw it.
 */
@Repository
public class ReviewWriter {

	private final JdbcClient jdbc;

	ReviewWriter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public void save(long listingId, List<StoreReview> reviews) {
		for (StoreReview review : reviews) {
			this.jdbc
				.sql("""
						INSERT INTO reviews (listing_id, external_id, rating, text, author, product_version, posted_at, reply)
						VALUES (:listing, :externalId, :rating, :text, :author, :productVersion, :postedAt, :reply)
						ON CONFLICT (listing_id, external_id) DO UPDATE SET
						    rating = EXCLUDED.rating,
						    text = EXCLUDED.text,
						    author = EXCLUDED.author,
						    product_version = EXCLUDED.product_version,
						    posted_at = EXCLUDED.posted_at,
						    reply = EXCLUDED.reply,
						    -- An edited review (other stars or text) is unread again.
						    read_at = CASE
						        WHEN reviews.rating IS DISTINCT FROM EXCLUDED.rating
						          OR reviews.text IS DISTINCT FROM EXCLUDED.text THEN NULL
						        ELSE reviews.read_at
						    END
						""")
				.param("listing", listingId)
				.param("externalId", review.externalId())
				.param("rating", review.rating())
				.param("text", review.text(), Types.VARCHAR)
				.param("author", review.author())
				.param("productVersion", review.productVersion(), Types.VARCHAR)
				.param("postedAt", OffsetDateTime.ofInstant(review.postedAt(), ZoneOffset.UTC))
				.param("reply", review.reply(), Types.VARCHAR)
				.update();
		}
	}

}
