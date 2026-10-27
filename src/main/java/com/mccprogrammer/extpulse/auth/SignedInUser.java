package com.mccprogrammer.extpulse.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Who is signed in to the dashboard, kept in the session. Page controllers in other
 * packages read it with {@code @AuthenticationPrincipal SignedInUser user}.
 */
public final class SignedInUser implements UserDetails, CredentialsContainer {

	private final long id;

	private final String email;

	private final Role role;

	private String passwordHash;

	SignedInUser(UserAccount account) {
		this.id = account.getId();
		this.email = account.getEmail();
		this.role = account.getRole();
		this.passwordHash = account.getPasswordHash();
	}

	public long id() {
		return this.id;
	}

	public String email() {
		return this.email;
	}

	public boolean isAdmin() {
		return this.role == Role.ADMIN;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return isAdmin() ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
				: List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	/** Spring Security calls this after login, so the hash isn't kept in the session. */
	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}

}
