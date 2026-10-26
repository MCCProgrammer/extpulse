package com.mccprogrammer.extpulse.collection;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mccprogrammer.extpulse.store.ListingNotFoundException;
import com.mccprogrammer.extpulse.store.ListingStats;
import com.mccprogrammer.extpulse.store.Store;
import com.mccprogrammer.extpulse.store.StoreException;
import com.mccprogrammer.extpulse.store.StoreSource;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import org.springframework.stereotype.Component;

/**
 * Calls a store the way the collection should: paced (one request per interval to
 * each store) and retried with growing waits when the store fails. A listing the
 * store doesn't have, or an ID it can't take, is not retried: asking again won't help.
 */
@Component
class ResilientStoreCalls {

	private static final double BACKOFF_MULTIPLIER = 4;

	// Paced calls queue for their turn; one that waits this long gives up.
	private static final Duration MAX_WAIT_FOR_TURN = Duration.ofMinutes(1);

	private final Retry retry;

	private final Map<Store, RateLimiter> paces = new EnumMap<>(Store.class);

	ResilientStoreCalls(CollectionProperties properties) {
		this.retry = Retry.of("stores", RetryConfig.custom()
				.maxAttempts(properties.attempts())
				.intervalFunction(IntervalFunction.ofExponentialBackoff(properties.firstRetryWait(), BACKOFF_MULTIPLIER))
				.retryExceptions(StoreException.class)
				.ignoreExceptions(ListingNotFoundException.class)
				.build());
		RateLimiterConfig pace = RateLimiterConfig.custom()
				.limitForPeriod(1)
				.limitRefreshPeriod(properties.minIntervalPerStore())
				.timeoutDuration(MAX_WAIT_FOR_TURN)
				.build();
		for (Store store : Store.values()) {
			this.paces.put(store, RateLimiter.of(store.name(), pace));
		}
	}

	ListingStats fetchStats(StoreSource source, String externalId) {
		// Every attempt, retries included, waits for its turn with the store.
		Supplier<ListingStats> paced = RateLimiter.decorateSupplier(this.paces.get(source.store()),
				() -> source.fetchStats(externalId));
		return Retry.decorateSupplier(this.retry, paced).get();
	}

}
