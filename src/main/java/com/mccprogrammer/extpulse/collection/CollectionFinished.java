package com.mccprogrammer.extpulse.collection;

import java.time.Instant;

/**
 * Published when a collection run ends, whatever its status. Other packages react to it
 * (alerts) without the collection knowing about them.
 */
public record CollectionFinished(long runId, CollectionRunStatus status, Instant finishedAt) {
}
