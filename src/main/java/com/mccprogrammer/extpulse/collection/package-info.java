/**
 * Daily collection: the scheduled job that asks every store adapter for stats and
 * reviews, saves one snapshot per listing per day and records each run.
 * <p>
 * May use only catalog, reviews, store (checked by {@code ModularityTests}).
 */
@ApplicationModule(allowedDependencies = { "catalog", "reviews", "store" })
package com.mccprogrammer.extpulse.collection;

import org.springframework.modulith.ApplicationModule;
