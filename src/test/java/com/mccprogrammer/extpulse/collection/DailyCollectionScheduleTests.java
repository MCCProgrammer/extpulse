package com.mccprogrammer.extpulse.collection;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.SimpleTriggerContext;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DailyCollectionScheduleTests {

	@Autowired
	private ScheduledTaskHolder scheduledTasks;

	@Test
	void runsEveryDayAtSixInTheMorningLisbonTime() {
		CronTask collection = this.scheduledTasks.getScheduledTasks()
			.stream()
			.map(ScheduledTask::getTask)
			.filter(CronTask.class::isInstance)
			.map(CronTask.class::cast)
			.filter(task -> task.toString().contains("DailyCollectionJob"))
			.findFirst()
			.orElseThrow();

		// 06:00 in Lisbon is 05:00 UTC in summer and 06:00 UTC in winter.
		assertThat(nextRunAfter(collection, "2026-07-15T00:00:00Z")).isEqualTo("2026-07-15T05:00:00Z");
		assertThat(nextRunAfter(collection, "2026-12-15T00:00:00Z")).isEqualTo("2026-12-15T06:00:00Z");
	}

	private Instant nextRunAfter(CronTask task, String instant) {
		SimpleTriggerContext context = new SimpleTriggerContext(
				Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
		return task.getTrigger().nextExecution(context);
	}

}
