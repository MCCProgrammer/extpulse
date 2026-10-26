package com.mccprogrammer.extpulse.collection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import com.mccprogrammer.extpulse.store.DiscoveredListing;
import com.mccprogrammer.extpulse.store.ListingStats;
import com.mccprogrammer.extpulse.store.Store;
import com.mccprogrammer.extpulse.store.StoreReview;
import com.mccprogrammer.extpulse.store.StoreSource;

/**
 * A store that answers what the test tells it to, and remembers when it was asked.
 * Queued answers are used first, in order; after that, {@code always}.
 */
class FakeStoreSource implements StoreSource {

	private final Store store;

	private final Deque<Supplier<ListingStats>> queued = new ArrayDeque<>();

	private Supplier<ListingStats> always = () -> {
		throw new IllegalStateException("No answer set up");
	};

	private final List<Long> callTimesNanos = new ArrayList<>();

	private Supplier<List<StoreReview>> reviews = List::of;

	FakeStoreSource(Store store) {
		this.store = store;
	}

	FakeStoreSource answers(ListingStats stats) {
		this.always = () -> stats;
		return this;
	}

	FakeStoreSource fails(RuntimeException ex) {
		this.always = () -> {
			throw ex;
		};
		return this;
	}

	FakeStoreSource thenFirst(RuntimeException ex) {
		this.queued.add(() -> {
			throw ex;
		});
		return this;
	}

	FakeStoreSource reviews(StoreReview... reviews) {
		this.reviews = () -> List.of(reviews);
		return this;
	}

	FakeStoreSource reviewsFail(RuntimeException ex) {
		this.reviews = () -> {
			throw ex;
		};
		return this;
	}

	int calls() {
		return this.callTimesNanos.size();
	}

	List<Long> callTimesNanos() {
		return this.callTimesNanos;
	}

	@Override
	public Store store() {
		return this.store;
	}

	@Override
	public List<DiscoveredListing> discover(String publisher) {
		return List.of();
	}

	@Override
	public List<StoreReview> fetchReviews(String externalId) {
		return this.reviews.get();
	}

	@Override
	public ListingStats fetchStats(String externalId) {
		this.callTimesNanos.add(System.nanoTime());
		Supplier<ListingStats> answer = this.queued.isEmpty() ? this.always : this.queued.poll();
		return answer.get();
	}

}
