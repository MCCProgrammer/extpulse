/**
 * Store adapters: one {@code StoreSource} per store (VS Code Marketplace, Open VSX,
 * Chrome Web Store). The only code that talks to the stores.
 * <p>
 * Depends on nothing else in ExtPulse.
 */
@ApplicationModule(allowedDependencies = {})
package com.mccprogrammer.extpulse.store;

import org.springframework.modulith.ApplicationModule;
