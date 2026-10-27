package com.mccprogrammer.extpulse.alerts;

import com.mccprogrammer.extpulse.collection.CollectionFinished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * When alerts are checked and sent: after every collection, every few minutes for
 * retries, and on Monday mornings for the weekly summary.
 */
@Component
class AlertJobs {

	private static final Logger logger = LoggerFactory.getLogger(AlertJobs.class);

	private final AlertService alerts;

	AlertJobs(AlertService alerts) {
		this.alerts = alerts;
	}

	@EventListener
	void afterCollection(CollectionFinished event) {
		// Runs inside the collection's call: a failure here must not turn into a failed
		// run.
		try {
			this.alerts.evaluate();
			this.alerts.deliverPending();
		}
		catch (RuntimeException ex) {
			logger.error("Checking alerts after collection run {} failed", event.runId(), ex);
		}
	}

	@Scheduled(cron = "${extpulse.alerts.delivery-cron}", zone = "${extpulse.zone}")
	void retryDelivery() {
		this.alerts.deliverPending();
	}

	@Scheduled(cron = "${extpulse.alerts.weekly-summary-cron}", zone = "${extpulse.zone}")
	void weeklySummary() {
		this.alerts.weeklySummary();
		this.alerts.deliverPending();
	}

}
