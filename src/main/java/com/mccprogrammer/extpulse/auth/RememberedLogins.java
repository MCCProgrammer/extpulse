package com.mccprogrammer.extpulse.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/**
 * "Keep me signed in" with persistent tokens. When a remembered cookie comes back with an
 * old token, someone else has a copy: Spring Security then deletes every remembered login
 * of the account and throws, which would end the request in a server error. Here the
 * visit just carries on signed out, so the browser lands on the sign-in page.
 */
class RememberedLogins extends PersistentTokenBasedRememberMeServices {

	private static final Logger logger = LoggerFactory.getLogger(RememberedLogins.class);

	RememberedLogins(String key, UserDetailsService accounts, PersistentTokenRepository tokens) {
		super(key, accounts, tokens);
	}

	@Override
	public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
		try {
			return super.autoLogin(request, response);
		}
		catch (CookieTheftException ex) {
			logger.warn("A remembered login was used with an old token; all of the account's remembered logins "
					+ "were ended");
			return null;
		}
	}

}
