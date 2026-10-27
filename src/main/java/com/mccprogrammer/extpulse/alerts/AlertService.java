package com.mccprogrammer.extpulse.alerts;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.mccprogrammer.extpulse.ApiProblem;
import com.mccprogrammer.extpulse.alerts.AlertDetector.Detection;
import com.mccprogrammer.extpulse.alerts.AlertOutbox.Pending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Rules, checking them and sending what they find. Checking writes alerts to the outbox;
 * delivery sends what the outbox holds, so the two can fail and retry on their own.
 */
@Service
class AlertService {

	private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

	// Looking back further than one collection makes up for a check that didn't run; the
	// outbox ignores what was already found.
	private static final Duration REVIEW_WINDOW = Duration.ofDays(2);

	// Possessive quantifiers and dot-free domain labels: no backtracking, so a long
	// crafted
	// string can't make matching slow (ReDoS).
	private static final Pattern EMAIL = Pattern.compile("[^@\\s]++@[^@\\s.]++(?:\\.[^@\\s.]++)++");

	private static final int MAX_EMAIL_LENGTH = 254;

	private final AlertRuleRepository rules;

	private final AlertDetector detector;

	private final AlertOutbox outbox;

	private final Map<AlertChannel, AlertSender> senders = new EnumMap<>(AlertChannel.class);

	private final DiscordAlertSender discord;

	private final AlertProperties properties;

	private final JdbcClient jdbc;

	private final Clock clock;

	AlertService(AlertRuleRepository rules, AlertDetector detector, AlertOutbox outbox, List<AlertSender> senders,
			DiscordAlertSender discord, AlertProperties properties, JdbcClient jdbc, Clock clock) {
		this.rules = rules;
		this.detector = detector;
		this.outbox = outbox;
		this.discord = discord;
		this.properties = properties;
		this.jdbc = jdbc;
		this.clock = clock;
		senders.forEach((sender) -> this.senders.put(sender.channel(), sender));
	}

	/** Checks every rule but the weekly summary; returns how many new alerts it found. */
	int evaluate() {
		Instant now = this.clock.instant();
		LocalDate today = LocalDate.now(this.clock);
		List<Detection> found = new ArrayList<>();
		found.addAll(this.detector.lowRatings(now.minus(REVIEW_WINDOW)));
		found.addAll(this.detector.newVersions(today));
		found.addAll(this.detector.installSpikes(today));
		found.addAll(this.detector.failingListings(today, now));
		return addAll(found);
	}

	/** The summary of last week (Monday to Sunday, in extpulse.zone). */
	int weeklySummary() {
		LocalDate thisMonday = LocalDate.now(this.clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		return addAll(this.detector.weeklySummaries(thisMonday.minusWeeks(1), this.clock.getZone()));
	}

	private int addAll(List<Detection> found) {
		int added = 0;
		for (Detection detection : found) {
			if (this.outbox.add(detection)) {
				added++;
			}
		}
		if (added > 0) {
			logger.info("{} new alert(s)", added);
		}
		return added;
	}

	/** Sends what the outbox holds; a failure is recorded and tried again later. */
	void deliverPending() {
		for (Pending alert : this.outbox.pending(this.properties.maxAttempts())) {
			try {
				this.senders.get(alert.channel())
					.send(alert.target(), new AlertMessage(alert.title(), alert.message()));
				this.outbox.sent(alert.id(), this.clock.instant());
			}
			catch (RuntimeException ex) {
				logger.warn("Alert {} to {} failed: {}", alert.id(), alert.channel(), ex.getMessage());
				this.outbox.failed(alert.id(), (ex.getMessage() != null) ? ex.getMessage() : ex.getClass().getName());
			}
		}
	}

	List<AlertRule> rulesOf(long userId) {
		return this.rules.findByUserIdOrderById(userId);
	}

	AlertRule addRule(long userId, AlertType type, AlertChannel channel, String target, Integer threshold) {
		String resolvedTarget = target(userId, channel, target);
		try {
			return this.rules
				.saveAndFlush(new AlertRule(userId, type, threshold(type, threshold), channel, resolvedTarget));
		}
		catch (DataIntegrityViolationException ex) {
			throw ApiProblem.of(HttpStatus.CONFLICT, "You already have this alert", ex);
		}
	}

	void deleteRule(long userId, long ruleId) {
		this.rules.delete(owned(userId, ruleId));
	}

	/**
	 * Sends a test message through the rule's channel now, so a wrong address shows at
	 * once.
	 */
	void sendTest(long userId, long ruleId) {
		AlertRule rule = owned(userId, ruleId);
		try {
			this.senders.get(rule.getChannel())
				.send(rule.getTarget(), new AlertMessage("ExtPulse test alert",
						"This is where your " + rule.getType() + " alerts will arrive."));
		}
		catch (RuntimeException ex) {
			throw ApiProblem.of(HttpStatus.BAD_GATEWAY, "The test alert could not be sent: " + ex.getMessage(), ex);
		}
	}

	private AlertRule owned(long userId, long ruleId) {
		return this.rules.findByIdAndUserId(ruleId, userId)
			.orElseThrow(() -> ApiProblem.of(HttpStatus.NOT_FOUND, "You have no alert " + ruleId));
	}

	private String target(long userId, AlertChannel channel, String target) {
		return switch (channel) {
			case EMAIL -> {
				if (target == null || target.isBlank()) {
					// The account's own email.
					yield this.jdbc.sql("SELECT email FROM users WHERE id = :id")
						.param("id", userId)
						.query(String.class)
						.single();
				}
				if (target.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(target.strip()).matches()) {
					throw ApiProblem.of(HttpStatus.BAD_REQUEST, "target must be an email address");
				}
				yield target.strip();
			}
			case DISCORD -> {
				if (!this.discord.isWebhook(target)) {
					throw ApiProblem.of(HttpStatus.BAD_REQUEST,
							"target must be a Discord webhook URL, like https://discord.com/api/webhooks/...");
				}
				yield target;
			}
		};
	}

	private static Integer threshold(AlertType type, Integer threshold) {
		return switch (type) {
			case LOW_RATING -> {
				int stars = (threshold != null) ? threshold : 2;
				if (stars < 1 || stars > 4) {
					throw ApiProblem.of(HttpStatus.BAD_REQUEST, "threshold for LOW_RATING is 1 to 4 stars");
				}
				yield stars;
			}
			case INSTALL_SPIKE -> {
				int minimum = (threshold != null) ? threshold : 10;
				if (minimum < 1) {
					throw ApiProblem.of(HttpStatus.BAD_REQUEST, "threshold for INSTALL_SPIKE is at least 1 install");
				}
				yield minimum;
			}
			default -> {
				if (threshold != null) {
					throw ApiProblem.of(HttpStatus.BAD_REQUEST, type + " has no threshold");
				}
				yield null;
			}
		};
	}

}
