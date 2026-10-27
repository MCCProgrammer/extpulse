package com.mccprogrammer.extpulse.alerts;

/**
 * Sends an alert through one channel. Throws when the channel refuses or can't be
 * reached, so the alert stays in the outbox to be tried again.
 */
interface AlertSender {

	AlertChannel channel();

	void send(String target, AlertMessage message);

}
