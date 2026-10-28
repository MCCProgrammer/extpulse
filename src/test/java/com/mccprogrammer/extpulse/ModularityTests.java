package com.mccprogrammer.extpulse;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModules;

/**
 * The package-per-feature structure, checked by Spring Modulith: each top-level package
 * is a module, modules form no cycle, and a module uses only the ones its package-info
 * allows ({@code @ApplicationModule(allowedDependencies = ...)}). A new dependency
 * between features is then a change to that list, made on purpose and reviewed.
 */
class ModularityTests {

	@Test
	void modulesUseOnlyTheModulesTheyAreAllowedTo() {
		ApplicationModules.of(ExtpulseApplication.class).verify();
	}

}
