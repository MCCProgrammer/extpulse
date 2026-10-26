package com.mccprogrammer.extpulse.store;

import java.util.Locale;

/**
 * An extension found on a store when looking up a publisher.
 *
 * @param externalId the ID the store uses for it, the one {@link StoreSource#fetchStats} takes
 * @param extensionKey the same for every store that has this extension: the VS Code
 * extension ID in lowercase ({@code mccprogrammer.diff-checker}), whether the store
 * writes it {@code MCCProgrammer.diff-checker} or {@code MCCProgrammer/diff-checker}
 * @param url the extension's page on the store
 */
public record DiscoveredListing(Store store, String externalId, String extensionKey, String displayName, String url) {

	/** The VS Code extension ID ({@code publisher.name}), compared ignoring case. */
	static String vsCodeExtensionKey(String publisher, String name) {
		return (publisher + "." + name).toLowerCase(Locale.ROOT);
	}

}
