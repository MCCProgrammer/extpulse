package com.mccprogrammer.extpulse.alerts;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.mccprogrammer.extpulse.alerts.AlertDetector.Detection;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The alerts to send (alert_events). An alert is written here first and sent after, so
 * a failed send is retried and a repeated detection is ignored.
 */
@Repository
class AlertOutbox {

	private static final int MAX_ERROR_LENGTH = 500;

	private final JdbcClient jdbc;

	AlertOutbox(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Adds the alert unless this rule already has one about the same thing; true if added. */
	boolean add(Detection detection) {
		return this.jdbc.sql("""
				INSERT INTO alert_events (rule_id, dedup_key, title, message)
				VALUES (:rule, :key, :title, :message)
				ON CONFLICT (rule_id, dedup_key) DO NOTHING
				""")
				.param("rule", detection.ruleId())
				.param("key", detection.dedupKey())
				.param("title", detection.message().title())
				.param("message", detection.message().body())
				.update() == 1;
	}

	/** Alerts not sent yet, with fewer than {@code maxAttempts} tries, oldest first. */
	List<Pending> pending(int maxAttempts) {
		return this.jdbc.sql("""
				SELECT ev.id, ev.title, ev.message, ar.channel, ar.target
				FROM alert_events ev JOIN alert_rules ar ON ar.id = ev.rule_id
				WHERE ev.sent_at IS NULL AND ev.attempts < :maxAttempts
				ORDER BY ev.id
				""")
				.param("maxAttempts", maxAttempts)
				.query(Pending.class)
				.list();
	}

	void sent(long id, Instant at) {
		this.jdbc.sql("UPDATE alert_events SET sent_at = :at, attempts = attempts + 1, last_error = NULL WHERE id = :id")
			.param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
			.param("id", id)
			.update();
	}

	void failed(long id, String error) {
		String message = (error.length() > MAX_ERROR_LENGTH) ? error.substring(0, MAX_ERROR_LENGTH) : error;
		this.jdbc.sql("UPDATE alert_events SET attempts = attempts + 1, last_error = :error WHERE id = :id")
			.param("error", message)
			.param("id", id)
			.update();
	}

	record Pending(long id, String title, String message, AlertChannel channel, String target) {
	}

}
