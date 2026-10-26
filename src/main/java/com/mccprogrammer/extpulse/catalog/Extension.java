package com.mccprogrammer.extpulse.catalog;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One extension, whatever the stores it is on. Its listings say where.
 */
@Entity
@Table(name = "extensions")
public class Extension {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long publisherId;

	private String displayName;

	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	protected Extension() {
	}

	Extension(long publisherId, String displayName) {
		this.publisherId = publisherId;
		this.displayName = displayName;
	}

	public Long getId() {
		return this.id;
	}

	public Long getPublisherId() {
		return this.publisherId;
	}

	public String getDisplayName() {
		return this.displayName;
	}

}
