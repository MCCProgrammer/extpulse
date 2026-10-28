/**
 * Reviews from every store in one inbox, with answered / unanswered status.
 * <p>
 * May use only auth, store (checked by {@code ModularityTests}).
 */
@ApplicationModule(allowedDependencies = { "auth", "store" })
package com.mccprogrammer.extpulse.reviews;

import org.springframework.modulith.ApplicationModule;
