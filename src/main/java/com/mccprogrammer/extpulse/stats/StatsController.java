package com.mccprogrammer.extpulse.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.mccprogrammer.extpulse.ApiProblem;
import com.mccprogrammer.extpulse.auth.AuthenticatedAccount;
import com.mccprogrammer.extpulse.stats.StatsService.ExtensionOverview;
import com.mccprogrammer.extpulse.stats.StatsService.Granularity;
import com.mccprogrammer.extpulse.stats.StatsService.Series;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StatsController {

	private final StatsService stats;

	StatsController(StatsService stats) {
		this.stats = stats;
	}

	/**
	 * Your extensions with their installs today and the gain over the last 7 days,
	 * largest first.
	 */
	@GetMapping("/api/extensions")
	List<ExtensionOverview> extensions(@AuthenticationPrincipal Jwt token) {
		return this.stats.overview(AuthenticatedAccount.id(token));
	}

	/**
	 * Installs over time, in total and per store. Dates are days in Lisbon; the default
	 * range is the last 30 days.
	 */
	@GetMapping("/api/extensions/{id}/stats")
	Series series(@AuthenticationPrincipal Jwt token, @PathVariable long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "day") String granularity) {
		return this.stats.series(AuthenticatedAccount.id(token), id, from, to, granularity(granularity));
	}

	private static Granularity granularity(String value) {
		try {
			return Granularity.valueOf(value.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw ApiProblem.of(HttpStatus.BAD_REQUEST, "granularity must be day or week");
		}
	}

}
