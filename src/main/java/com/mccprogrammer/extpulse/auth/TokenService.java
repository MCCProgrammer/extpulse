package com.mccprogrammer.extpulse.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues the login token. The subject is the account ID; "scope" holds what the account
 * may do, which Spring Security reads as SCOPE_user / SCOPE_admin.
 */
@Service
class TokenService {

	private final JwtEncoder encoder;

	private final AuthProperties properties;

	private final Clock clock;

	TokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	IssuedToken issue(UserAccount account) {
		Instant now = this.clock.instant();
		Instant expiresAt = now.plus(this.properties.tokenValidity());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(SecurityConfiguration.ISSUER)
			.subject(account.getId().toString())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.claim("email", account.getEmail())
			.claim("scope", (account.getRole() == Role.ADMIN) ? "user admin" : "user")
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	record IssuedToken(String value, Instant expiresAt) {
	}

}
