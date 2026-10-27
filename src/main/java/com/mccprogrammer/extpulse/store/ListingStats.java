package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a store shows for one listing at one moment.
 *
 * @param installs installs on the Marketplace, downloads on Open VSX, users on the Chrome
 * Web Store
 * @param ratingAvg the average rating, or {@code null} while there are no ratings
 * @param version the latest published version, or {@code null} if the store doesn't show
 * it
 */
public record ListingStats(long installs, BigDecimal ratingAvg, int ratingCount, String version) {

	public ListingStats {
		if (installs < 0 || ratingCount < 0) {
			throw new IllegalArgumentException("Negative installs or rating count: " + installs + ", " + ratingCount);
		}
		// Two decimals, as stored in snapshots.rating_avg.
		if (ratingAvg != null) {
			ratingAvg = ratingAvg.setScale(2, RoundingMode.HALF_UP);
		}
	}

}
