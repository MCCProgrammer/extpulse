/**
 * Statistics read from the saved snapshots: combined totals, daily and weekly gains,
 * split by store.
 * <p>
 * May use only auth, catalog, store (checked by {@code ModularityTests}).
 */
@ApplicationModule(allowedDependencies = { "auth", "catalog", "store" })
package com.mccprogrammer.extpulse.stats;

import org.springframework.modulith.ApplicationModule;
