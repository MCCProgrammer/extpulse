/**
 * Public portfolio page and the combined installs badge, served without login.
 * <p>
 * May use only auth, stats (checked by {@code ModularityTests}).
 */
@ApplicationModule(allowedDependencies = { "auth", "stats" })
package com.mccprogrammer.extpulse.publicpage;

import org.springframework.modulith.ApplicationModule;
