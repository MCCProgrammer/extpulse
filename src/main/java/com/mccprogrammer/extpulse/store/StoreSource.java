package com.mccprogrammer.extpulse.store;

import java.util.List;

/**
 * One store, seen from the rest of the application. Adding a store means writing one more
 * implementation; nothing else changes.
 */
public interface StoreSource {

	Store store();

	/**
	 * Every extension the publisher has on this store, or an empty list when the
	 * publisher isn't there.
	 */
	List<DiscoveredListing> discover(String publisher);

	/**
	 * What the store shows for one listing right now.
	 * @param externalId the listing's ID on this store
	 * @throws ListingNotFoundException if the store doesn't have it
	 * @throws StoreException if the store can't be reached or answers in an unexpected
	 * way
	 */
	ListingStats fetchStats(String externalId);

	/**
	 * The listing's latest reviews, up to 100, without deleted or hidden ones.
	 * @throws ListingNotFoundException if the store doesn't have the listing
	 * @throws StoreException if the store can't be reached or answers in an unexpected
	 * way
	 */
	List<StoreReview> fetchReviews(String externalId);

}
