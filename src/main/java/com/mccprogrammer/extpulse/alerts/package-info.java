/**
 * Alerts: rules a user sets (low ratings, install spikes, new versions, a listing that
 * stopped being collected, a weekly summary), checked after every collection and sent by
 * email or to a Discord channel, never twice for the same thing.
 * <p>
 * May use only auth, collection, store (checked by {@code ModularityTests}).
 */
@ApplicationModule(allowedDependencies = { "auth", "collection", "store" })
package com.mccprogrammer.extpulse.alerts;

import org.springframework.modulith.ApplicationModule;
