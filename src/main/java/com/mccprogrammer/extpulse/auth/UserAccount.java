package com.mccprogrammer.extpulse.auth;

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
 * A person who can log in. Named UserAccount so it doesn't clash with Spring
 * Security's own {@code User}.
 */
@Entity
@Table(name = "users")
public class UserAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String email;

	private String passwordHash;

	@Enumerated(EnumType.STRING)
	private Role role;

	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	protected UserAccount() {
	}

	UserAccount(String email, String passwordHash) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = Role.USER;
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	String getPasswordHash() {
		return this.passwordHash;
	}

	public Role getRole() {
		return this.role;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
