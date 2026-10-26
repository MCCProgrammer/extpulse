package com.mccprogrammer.extpulse.collection;

import java.time.Duration;
import java.util.List;

import com.mccprogrammer.extpulse.store.ListingNotFoundException;
import com.mccprogrammer.extpulse.store.ListingStats;
import com.mccprogrammer.extpulse.store.Store;
import com.mccprogrammer.extpulse.store.StoreException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientStoreCallsTests {

	private static final ListingStats STATS = new ListingStats(1764, null, 0, "0.3.0");

	private static final StoreException STORE_DOWN = new StoreException(Store.OPEN_VSX, "503");

	// Same shape as application.yaml (4 attempts, waits growing 4 times), in milliseconds.
	private final ResilientStoreCalls calls = new ResilientStoreCalls(
			new CollectionProperties(4, Duration.ofMillis(20), Duration.ofMillis(1)));

	@Test
	void retriesUntilTheStoreAnswers() {
		FakeStoreSource store = new FakeStoreSource(Store.OPEN_VSX).thenFirst(STORE_DOWN)
			.thenFirst(STORE_DOWN)
			.answers(STATS);

		assertThat(this.calls.fetchStats(store, "MCCProgrammer/diff-checker")).isEqualTo(STATS);
		assertThat(store.calls()).isEqualTo(3);
	}

	@Test
	void givesUpAfterFourAttemptsWaitingLongerEachTime() {
		FakeStoreSource store = new FakeStoreSource(Store.OPEN_VSX).fails(STORE_DOWN);

		assertThatThrownBy(() -> this.calls.fetchStats(store, "MCCProgrammer/diff-checker")).isSameAs(STORE_DOWN);

		List<Long> times = store.callTimesNanos();
		assertThat(times).hasSize(4);
		long first = times.get(1) - times.get(0);
		long second = times.get(2) - times.get(1);
		long third = times.get(3) - times.get(2);
		assertThat(Duration.ofNanos(first)).isGreaterThanOrEqualTo(Duration.ofMillis(20));
		assertThat(second).isGreaterThan(first * 2);
		assertThat(third).isGreaterThan(second * 2);
	}

	@Test
	void doesNotRetryAListingTheStoreDoesNotHave() {
		FakeStoreSource store = new FakeStoreSource(Store.OPEN_VSX)
			.fails(new ListingNotFoundException(Store.OPEN_VSX, "MCCProgrammer/gone"));

		assertThatThrownBy(() -> this.calls.fetchStats(store, "MCCProgrammer/gone"))
			.isInstanceOf(ListingNotFoundException.class);
		assertThat(store.calls()).isEqualTo(1);
	}

	@Test
	void doesNotRetryAnIdTheStoreCannotTake() {
		FakeStoreSource store = new FakeStoreSource(Store.OPEN_VSX)
			.fails(new IllegalArgumentException("Not an Open VSX ID"));

		assertThatThrownBy(() -> this.calls.fetchStats(store, "nonsense")).isInstanceOf(IllegalArgumentException.class);
		assertThat(store.calls()).isEqualTo(1);
	}

	@Test
	void pacesRequestsToTheSameStoreButNotAcrossStores() {
		ResilientStoreCalls paced = new ResilientStoreCalls(
				new CollectionProperties(1, Duration.ofMillis(1), Duration.ofMillis(300)));
		FakeStoreSource marketplace = new FakeStoreSource(Store.VS_MARKETPLACE).answers(STATS);
		FakeStoreSource openVsx = new FakeStoreSource(Store.OPEN_VSX).answers(STATS);

		paced.fetchStats(marketplace, "a");
		paced.fetchStats(openVsx, "b");
		paced.fetchStats(marketplace, "c");

		long otherStore = openVsx.callTimesNanos().get(0) - marketplace.callTimesNanos().get(0);
		long sameStore = marketplace.callTimesNanos().get(1) - marketplace.callTimesNanos().get(0);
		assertThat(Duration.ofNanos(otherStore)).isLessThan(Duration.ofMillis(150));
		assertThat(Duration.ofNanos(sameStore)).isGreaterThanOrEqualTo(Duration.ofMillis(150));
	}

}
