package com.mccprogrammer.extpulse.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Looks accounts up by email for the dashboard's login form. Spring Security checks the
 * password, and also hashes one for an unknown email, so both cases take as long.
 */
@Service
class AccountDetailsService implements UserDetailsService {

	private final UserAccountRepository accounts;

	AccountDetailsService(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return this.accounts.findByEmail(email.strip())
			.map(SignedInUser::new)
			.orElseThrow(() -> new UsernameNotFoundException("No account for this email"));
	}

}
