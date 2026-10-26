package com.mccprogrammer.extpulse.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads who is calling from a verified login token, for controllers in other packages.
 */
public final class AuthenticatedAccount {

	private AuthenticatedAccount() {
	}

	/** The account ID, which login tokens carry as their subject. */
	public static long id(Jwt token) {
		return Long.parseLong(token.getSubject());
	}

}
