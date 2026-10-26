package com.mccprogrammer.extpulse.store;

import java.time.Instant;

/**
 * A review as a store shows it.
 *
 * @param externalId what identifies the review on its listing: the review ID on the
 * Marketplace; on Open VSX, which has no review IDs and allows one review per user,
 * the author ({@code github:login})
 * @param text the comment, or {@code null} for a rating without one
 * @param productVersion the version reviewed, if the store says
 * @param postedAt when it was written or last edited
 * @param reply the publisher's reply, on stores that have replies (the Marketplace)
 */
public record StoreReview(String externalId, int rating, String text, String author, String productVersion,
		Instant postedAt, String reply) {
}
