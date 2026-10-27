package com.mccprogrammer.extpulse.store;

/**
 * The stores ExtPulse reads. The names match the values allowed in
 * {@code listings.store}.
 */
public enum Store {

	VS_MARKETPLACE("VS Code Marketplace"),

	OPEN_VSX("Open VSX"),

	CHROME_WEB_STORE("Chrome Web Store");

	private final String displayName;

	Store(String displayName) {
		this.displayName = displayName;
	}

	/** The store's name as people write it, for messages. */
	public String displayName() {
		return this.displayName;
	}

}
