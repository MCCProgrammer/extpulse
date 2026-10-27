package com.mccprogrammer.extpulse.alerts;

import java.util.List;

import com.mccprogrammer.extpulse.auth.AuthenticatedAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AlertController {

	private final AlertService alerts;

	AlertController(AlertService alerts) {
		this.alerts = alerts;
	}

	@GetMapping("/api/alerts")
	List<RuleResponse> rules(@AuthenticationPrincipal Jwt token) {
		return this.alerts.rulesOf(AuthenticatedAccount.id(token)).stream().map(RuleResponse::of).toList();
	}

	/**
	 * A new alert. EMAIL without a target goes to your account's email; DISCORD needs the
	 * channel's webhook URL. threshold: stars for LOW_RATING (default 2), the smallest
	 * day's gain for INSTALL_SPIKE (default 10).
	 */
	@PostMapping("/api/alerts")
	@ResponseStatus(HttpStatus.CREATED)
	RuleResponse add(@AuthenticationPrincipal Jwt token, @Valid @RequestBody NewRule rule) {
		return RuleResponse.of(this.alerts.addRule(AuthenticatedAccount.id(token), rule.type(), rule.channel(),
				rule.target(), rule.threshold()));
	}

	@DeleteMapping("/api/alerts/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		this.alerts.deleteRule(AuthenticatedAccount.id(token), id);
	}

	/** Sends a test message to the alert's email or channel now. */
	@PostMapping("/api/alerts/{id}/test")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void test(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		this.alerts.sendTest(AuthenticatedAccount.id(token), id);
	}

	record NewRule(@NotNull AlertType type, @NotNull AlertChannel channel, @Size(max = 500) String target,
			Integer threshold) {
	}

	/**
	 * @param target the email, or the Discord webhook with its secret token hidden
	 */
	record RuleResponse(long id, AlertType type, Integer threshold, AlertChannel channel, String target) {

		static RuleResponse of(AlertRule rule) {
			return new RuleResponse(rule.getId(), rule.getType(), rule.getThreshold(), rule.getChannel(),
					(rule.getChannel() == AlertChannel.DISCORD) ? hideToken(rule.getTarget()) : rule.getTarget());
		}

		// A webhook URL is .../webhooks/{id}/{token}; anyone with the token can post to the channel.
		private static String hideToken(String url) {
			int lastSlash = url.lastIndexOf('/');
			return (lastSlash > 0) ? url.substring(0, lastSlash + 1) + "****" : "****";
		}

	}

}
