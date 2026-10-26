package com.mccprogrammer.extpulse.catalog;

import java.time.Instant;

import com.mccprogrammer.extpulse.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An extension's presence on one store.
 */
@Entity
@Table(name = "listings")
public class Listing {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long extensionId;

	@Enumerated(EnumType.STRING)
	private Store store;

	private String externalId;

	private String url;

	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	protected Listing() {
	}

	Listing(long extensionId, Store store, String externalId, String url) {
		this.extensionId = extensionId;
		this.store = store;
		this.externalId = externalId;
		this.url = url;
	}

	public Long getId() {
		return this.id;
	}

	public Long getExtensionId() {
		return this.extensionId;
	}

	public Store getStore() {
		return this.store;
	}

	public String getExternalId() {
		return this.externalId;
	}

	public String getUrl() {
		return this.url;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
