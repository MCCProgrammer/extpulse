package com.mccprogrammer.extpulse.store;

/**
 * The store answered, and it doesn't have this listing (unpublished, or a wrong ID).
 */
public class ListingNotFoundException extends StoreException {

	public ListingNotFoundException(Store store, String externalId) {
		super(store, "no listing " + externalId);
	}

}
