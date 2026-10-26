package com.mccprogrammer.extpulse.store;

/**
 * An extension found on a store when looking up a publisher.
 *
 * @param externalId the ID the store uses for it, the one {@link StoreSource#fetchStats} takes
 * @param url the extension's page on the store
 */
public record DiscoveredListing(Store store, String externalId, String displayName, String url) {
}
