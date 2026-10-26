package com.mccprogrammer.extpulse.auth;

import java.time.Instant;

import com.mccprogrammer.extpulse.auth.TokenService.IssuedToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

	private final AccountService accounts;

	AuthController(AccountService accounts) {
		this.accounts = accounts;
	}

	@PostMapping("/api/auth/register")
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse register(@Valid @RequestBody RegisterRequest request) {
		return AccountResponse.of(this.accounts.register(request.email(), request.password()));
	}

	@PostMapping("/api/auth/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		IssuedToken token = this.accounts.login(request.email(), request.password());
		return new TokenResponse(token.value(), "Bearer", token.expiresAt());
	}

	/** The account the token belongs to. */
	@GetMapping("/api/me")
	AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
		return AccountResponse.of(this.accounts.get(Long.parseLong(jwt.getSubject())));
	}

	record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 10, max = 72) String password) {
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	record AccountResponse(long id, String email, Role role) {

		static AccountResponse of(UserAccount account) {
			return new AccountResponse(account.getId(), account.getEmail(), account.getRole());
		}

	}

	record TokenResponse(String token, String tokenType, Instant expiresAt) {
	}

}
