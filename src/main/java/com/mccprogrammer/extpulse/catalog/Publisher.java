package com.mccprogrammer.extpulse.catalog;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A publisher account on the stores (for example, MCCProgrammer), added by one user.
 * The user is referenced by ID: accounts belong to the auth package.
 */
@Entity
@Table(name = "publishers")
public class Publisher {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	private String name;

	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	protected Publisher() {
	}

	Publisher(long userId, String name) {
		this.userId = userId;
		this.name = name;
	}

	public Long getId() {
		return this.id;
	}

	public Long getUserId() {
		return this.userId;
	}

	public String getName() {
		return this.name;
	}

}
