package com.mccprogrammer.extpulse.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import com.mccprogrammer.extpulse.auth.TokenService.IssuedToken;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

@Service
class AccountService {

	// BCrypt only reads the first 72 bytes; a longer password would be silently cut.
	private static final int MAX_PASSWORD_BYTES = 72;

	private final UserAccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokens;

	// Checked when the email is unknown, so a wrong email takes as long as a wrong
	// password and response times don't reveal which emails have accounts.
	private final String unknownAccountHash;

	AccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, TokenService tokens) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.unknownAccountHash = passwordEncoder.encode("no account has this password");
	}

	UserAccount register(String email, String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw new ErrorResponseException(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(
					HttpStatus.BAD_REQUEST, "The password is longer than " + MAX_PASSWORD_BYTES + " bytes"), null);
		}
		String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
		if (this.accounts.findByEmail(normalizedEmail).isPresent()) {
			throw new EmailAlreadyRegisteredException();
		}
		try {
			return this.accounts.save(new UserAccount(normalizedEmail, this.passwordEncoder.encode(password)));
		}
		catch (DataIntegrityViolationException ex) {
			// Two registrations with the same email at the same moment: the unique index decides.
			throw new EmailAlreadyRegisteredException();
		}
	}

	IssuedToken login(String email, String password) {
		Optional<UserAccount> account = this.accounts.findByEmail(email.strip());
		String hash = account.map(UserAccount::getPasswordHash).orElse(this.unknownAccountHash);
		boolean matches = this.passwordEncoder.matches(password, hash);
		if (account.isEmpty() || !matches) {
			throw new InvalidCredentialsException();
		}
		return this.tokens.issue(account.get());
	}

	UserAccount get(long id) {
		return this.accounts.findById(id).orElseThrow(InvalidCredentialsException::new);
	}

	static class EmailAlreadyRegisteredException extends ErrorResponseException {

		EmailAlreadyRegisteredException() {
			super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
					"An account with this email already exists"), null);
		}

	}

	static class InvalidCredentialsException extends ErrorResponseException {

		InvalidCredentialsException() {
			super(HttpStatus.UNAUTHORIZED,
					ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Wrong email or password"), null);
		}

	}

}
