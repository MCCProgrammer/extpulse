package com.mccprogrammer.extpulse.alerts;

/**
 * What a rule watches. The names match the values allowed in {@code alert_rules.type}.
 */
public enum AlertType {

	/** A new review with at most {@code threshold} stars (default 2). */
	LOW_RATING,

	/**
	 * A day's gain on a listing of at least {@code threshold} (default 10) and more than
	 * twice its usual.
	 */
	INSTALL_SPIKE,

	/** A listing shows a new version. */
	NEW_VERSION,

	/** A listing has had no new numbers for 2 days. */
	COLLECTION_FAILING,

	/** Mondays: the week's gains and new reviews. */
	WEEKLY_SUMMARY

}
