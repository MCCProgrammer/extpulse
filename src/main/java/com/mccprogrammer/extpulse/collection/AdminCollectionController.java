package com.mccprogrammer.extpulse.collection;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the collection now, for admins (/api/admin/** needs the ADMIN role): to recover a
 * day whose run failed, or to see the first numbers right after adding a publisher. Safe
 * next to the scheduled run, since snapshots are upserted.
 */
@RestController
class AdminCollectionController {

	private final CollectionService collection;

	AdminCollectionController(CollectionService collection) {
		this.collection = collection;
	}

	@PostMapping("/api/admin/collect")
	RunResponse collect() {
		CollectionRun run = this.collection.collect();
		return new RunResponse(run.getId(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
				run.getListingsSucceeded(), run.getListingsFailed(), run.getErrors());
	}

	record RunResponse(long id, CollectionRunStatus status, Instant startedAt, Instant finishedAt, int collected,
			int failed, List<CollectionError> errors) {
	}

}
