package com.mccprogrammer.extpulse.collection;

public enum CollectionRunStatus {

	RUNNING,

	/** Every listing was collected. */
	SUCCEEDED,

	/** Some listings were collected and some failed. */
	PARTIAL,

	/** No listing was collected. */
	FAILED

}
