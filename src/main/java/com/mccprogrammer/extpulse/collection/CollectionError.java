package com.mccprogrammer.extpulse.collection;

import com.mccprogrammer.extpulse.store.Store;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One listing that failed in a run, and why.
 */
@Embeddable
public record CollectionError(Long listingId, @Enumerated(EnumType.STRING) Store store, String externalId,
		String message) {
}
