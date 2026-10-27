package com.mccprogrammer.extpulse.auth;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.mccprogrammer.extpulse.ApiProblem;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which accounts have a public page, and at what address. An account is private until its
 * owner chooses an address; removing it makes the page and badges private again.
 */
@Service
public class PublicProfiles {

	// The same rule as the column's CHECK constraint (V6).
	private static final Pattern SLUG = Pattern.compile("[a-z0-9]([a-z0-9-]{0,38}[a-z0-9])?");

	private final UserAccountRepository accounts;

	PublicProfiles(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	/** The account whose public page is at this address. */
	public Optional<Long> ownerOf(String slug) {
		return this.accounts.findByPublicSlug(slug.toLowerCase(Locale.ROOT)).map(UserAccount::getId);
	}

	public Optional<String> slugOf(long userId) {
		return this.accounts.findById(userId).map(UserAccount::getPublicSlug);
	}

	/** Sets the address of the account's public page; an empty one removes the page. */
	@Transactional
	void choose(long userId, String slug) {
		String normalized = (slug != null && !slug.isBlank()) ? slug.strip().toLowerCase(Locale.ROOT) : null;
		if (normalized != null && !SLUG.matcher(normalized).matches()) {
			throw ApiProblem.of(HttpStatus.BAD_REQUEST,
					"Use 1 to 40 lowercase letters, digits and dashes, not starting or ending with a dash");
		}
		UserAccount account = this.accounts.findById(userId)
			.orElseThrow(() -> ApiProblem.of(HttpStatus.NOT_FOUND, "No account " + userId));
		account.setPublicSlug(normalized);
		try {
			this.accounts.saveAndFlush(account);
		}
		catch (DataIntegrityViolationException ex) {
			throw ApiProblem.of(HttpStatus.CONFLICT, "Another account already uses " + normalized, ex);
		}
	}

}
