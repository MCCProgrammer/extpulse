package com.mccprogrammer.extpulse.alerts;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One thing a user wants to be told, and where: for example, reviews of 2 stars or
 * less, to a Discord channel.
 */
@Entity
@Table(name = "alert_rules")
class AlertRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	@Enumerated(EnumType.STRING)
	private AlertType type;

	private Integer threshold;

	@Enumerated(EnumType.STRING)
	private AlertChannel channel;

	private String target;

	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	protected AlertRule() {
	}

	AlertRule(long userId, AlertType type, Integer threshold, AlertChannel channel, String target) {
		this.userId = userId;
		this.type = type;
		this.threshold = threshold;
		this.channel = channel;
		this.target = target;
	}

	Long getId() {
		return this.id;
	}

	Long getUserId() {
		return this.userId;
	}

	AlertType getType() {
		return this.type;
	}

	Integer getThreshold() {
		return this.threshold;
	}

	AlertChannel getChannel() {
		return this.channel;
	}

	String getTarget() {
		return this.target;
	}

}
