package com.mccprogrammer.extpulse.collection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * One run of the collection: when it ran, how many listings it collected and which
 * ones failed.
 */
@Entity
@Table(name = "collection_runs")
public class CollectionRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Instant startedAt;

	private Instant finishedAt;

	@Enumerated(EnumType.STRING)
	private CollectionRunStatus status;

	private int listingsTotal;

	private int listingsSucceeded;

	private int listingsFailed;

	@ElementCollection
	@CollectionTable(name = "collection_run_errors", joinColumns = @JoinColumn(name = "run_id"))
	private List<CollectionError> errors = new ArrayList<>();

	protected CollectionRun() {
	}

	static CollectionRun startedAt(Instant now) {
		CollectionRun run = new CollectionRun();
		run.startedAt = now;
		run.status = CollectionRunStatus.RUNNING;
		return run;
	}

	void succeeded() {
		this.listingsTotal++;
		this.listingsSucceeded++;
	}

	void failed(CollectionError error) {
		this.listingsTotal++;
		this.listingsFailed++;
		this.errors.add(error);
	}

	void finish(Instant now) {
		this.finishedAt = now;
		if (this.listingsFailed == 0) {
			this.status = CollectionRunStatus.SUCCEEDED;
		}
		else if (this.listingsSucceeded == 0) {
			this.status = CollectionRunStatus.FAILED;
		}
		else {
			this.status = CollectionRunStatus.PARTIAL;
		}
	}

	public Long getId() {
		return this.id;
	}

	public Instant getStartedAt() {
		return this.startedAt;
	}

	public Instant getFinishedAt() {
		return this.finishedAt;
	}

	public CollectionRunStatus getStatus() {
		return this.status;
	}

	public int getListingsTotal() {
		return this.listingsTotal;
	}

	public int getListingsSucceeded() {
		return this.listingsSucceeded;
	}

	public int getListingsFailed() {
		return this.listingsFailed;
	}

	public List<CollectionError> getErrors() {
		return List.copyOf(this.errors);
	}

}
