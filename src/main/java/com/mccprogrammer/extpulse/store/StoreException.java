package com.mccprogrammer.extpulse.store;

/**
 * A store couldn't be read: it didn't answer, answered with an error, or answered with
 * something ExtPulse doesn't understand (for example, a changed page).
 */
public class StoreException extends RuntimeException {

	private final Store store;

	public StoreException(Store store, String message) {
		super(store + ": " + message);
		this.store = store;
	}

	public StoreException(Store store, String message, Throwable cause) {
		super(store + ": " + message, cause);
		this.store = store;
	}

	public Store store() {
		return this.store;
	}

}
