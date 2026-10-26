package com.mccprogrammer.extpulse.collection;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the collection every day (extpulse.collection.cron, in extpulse.zone).
 */
@Component
class DailyCollectionJob {

	private final CollectionService collection;

	DailyCollectionJob(CollectionService collection) {
		this.collection = collection;
	}

	@Scheduled(cron = "${extpulse.collection.cron}", zone = "${extpulse.zone}")
	void run() {
		this.collection.collect();
	}

}
