package com.mccprogrammer.extpulse.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * What every page may need: the signed-in user (or null), for the navigation bar, and the
 * site's address, for links meant to be copied (badges in a README).
 */
@ControllerAdvice(annotations = Controller.class)
class CurrentUserAdvice {

	@ModelAttribute("currentUser")
	SignedInUser currentUser(@AuthenticationPrincipal SignedInUser user) {
		return user;
	}

	/** As the visitor sees it: behind the proxy, from its X-Forwarded-* headers. */
	@ModelAttribute("baseUrl")
	String baseUrl() {
		return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
	}

}
