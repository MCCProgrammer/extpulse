/**
 * What ExtPulse tracks: publishers, their extensions and each extension's listings on the
 * stores. The other packages read listings from here.
 * <p>
 * May use only auth, store (checked by {@code ModularityTests}).
 */
@ApplicationModule(allowedDependencies = { "auth", "store" })
package com.mccprogrammer.extpulse.catalog;

import org.springframework.modulith.ApplicationModule;
