package com.mccprogrammer.extpulse.collection;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mccprogrammer.extpulse.catalog.Listing;
import com.mccprogrammer.extpulse.catalog.ListingRepository;
import com.mccprogrammer.extpulse.reviews.ReviewWriter;
import com.mccprogrammer.extpulse.store.Store;
import com.mccprogrammer.extpulse.store.StoreSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Collects today's snapshot and the reviews of every listing, and records the run.
 */
@Service
public class CollectionService {

	private static final Logger logger = LoggerFactory.getLogger(CollectionService.class);

	private static final int MAX_MESSAGE_LENGTH = 500;

	private final ListingRepository listings;

	private final CollectionRunRepository runs;

	private final SnapshotWriter snapshots;

	private final ReviewWriter reviews;

	private final ResilientStoreCalls calls;

	private final Map<Store, StoreSource> sources = new EnumMap<>(Store.class);

	private final Clock clock;

	private final ApplicationEventPublisher events;

	CollectionService(ListingRepository listings, CollectionRunRepository runs, SnapshotWriter snapshots,
			ReviewWriter reviews, ResilientStoreCalls calls, List<StoreSource> sources, Clock clock,
			ApplicationEventPublisher events) {
		this.listings = listings;
		this.runs = runs;
		this.snapshots = snapshots;
		this.reviews = reviews;
		this.calls = calls;
		this.clock = clock;
		this.events = events;
		for (StoreSource source : sources) {
			if (this.sources.put(source.store(), source) != null) {
				throw new IllegalStateException("Two sources for " + source.store());
			}
		}
	}

	/**
	 * Safe to run more than once a day: the day's snapshot is updated, never duplicated.
	 * No transaction spans the run, so the store calls never hold a database connection;
	 * each snapshot is saved as soon as it's read.
	 */
	public CollectionRun collect() {
		CollectionRun run = this.runs.save(CollectionRun.startedAt(this.clock.instant()));
		LocalDate today = LocalDate.now(this.clock);
		List<Listing> all = this.listings.findAll(Sort.by("id"));
		logger.info("Collection run {} started: {} listings for {}", run.getId(), all.size(), today);
		for (Listing listing : all) {
			String step = "stats";
			try {
				StoreSource source = this.sources.get(listing.getStore());
				if (source == null) {
					throw new IllegalStateException("No source for " + listing.getStore());
				}
				this.snapshots.save(listing.getId(), today, this.calls.fetchStats(source, listing.getExternalId()));
				// Saved snapshot first: reviews failing doesn't lose the day's numbers.
				step = "reviews";
				this.reviews.save(listing.getId(), this.calls.fetchReviews(source, listing.getExternalId()));
				run.succeeded();
			}
			catch (RuntimeException ex) {
				// One listing failing (a store down, a page that changed) never stops the others.
				logger.warn("Collection run {}: {} of {} {} failed: {}", run.getId(), step, listing.getStore(),
						listing.getExternalId(), ex.getMessage());
				run.failed(new CollectionError(listing.getId(), listing.getStore(), listing.getExternalId(),
						message(step, ex)));
			}
		}
		run.finish(this.clock.instant());
		CollectionRun finished = this.runs.save(run);
		logger.info("Collection run {} {}: {} collected, {} failed", finished.getId(), finished.getStatus(),
				finished.getListingsSucceeded(), finished.getListingsFailed());
		this.events.publishEvent(
				new CollectionFinished(finished.getId(), finished.getStatus(), finished.getFinishedAt()));
		return finished;
	}

	private static String message(String step, RuntimeException ex) {
		String message = step + ": " + ((ex.getMessage() != null) ? ex.getMessage() : ex.getClass().getName());
		return (message.length() > MAX_MESSAGE_LENGTH) ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
	}

}
