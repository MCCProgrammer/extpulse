package com.mccprogrammer.extpulse.collection;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How hard the collection tries ({@code extpulse.collection.*} in application.yaml).
 *
 * @param attempts calls to a store per listing, the first one included
 * @param firstRetryWait the wait before the first retry; each later wait is four times longer
 * @param minIntervalPerStore the shortest time between two requests to the same store
 */
@ConfigurationProperties("extpulse.collection")
record CollectionProperties(int attempts, Duration firstRetryWait, Duration minIntervalPerStore) {
}
