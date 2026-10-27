package com.mccprogrammer.extpulse.collection;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The admin page (/app/admin/** needs the ADMIN role): the last two weeks of collection
 * runs, and a button that runs the collection now.
 */
@Controller
class AdminPages {

	private final CollectionService collection;

	private final CollectionRunRepository runs;

	private final Clock clock;

	AdminPages(CollectionService collection, CollectionRunRepository runs, Clock clock) {
		this.collection = collection;
		this.runs = runs;
		this.clock = clock;
	}

	@GetMapping("/app/admin")
	String admin(Model model) {
		model.addAttribute("runs",
				this.runs.findTop14ByOrderByStartedAtDesc()
					.stream()
					.map((run) -> RunRow.of(run, this.clock.getZone()))
					.toList());
		return "collection/admin";
	}

	@PostMapping("/app/admin/collect")
	String collect(RedirectAttributes redirect) {
		CollectionRun run = this.collection.collect();
		String summary = "Collection " + run.getStatus().name().toLowerCase(Locale.ROOT) + ": "
				+ run.getListingsSucceeded() + " of " + run.getListingsTotal() + " listings.";
		if (run.getErrors().isEmpty()) {
			redirect.addFlashAttribute("notice", summary);
		}
		else {
			redirect.addFlashAttribute("error",
					summary + " Failed: "
							+ run.getErrors()
								.stream()
								.map((error) -> error.externalId() + " (" + error.message() + ")")
								.collect(Collectors.joining("; ")));
		}
		return "redirect:/app/admin";
	}

	/**
	 * A run as the page shows it: the start in the app's time zone, and how long it took
	 * (null while it runs).
	 */
	record RunRow(ZonedDateTime startedAt, CollectionRunStatus status, int succeeded, int failed, Long seconds) {

		static RunRow of(CollectionRun run, ZoneId zone) {
			Long seconds = (run.getFinishedAt() != null)
					? Duration.between(run.getStartedAt(), run.getFinishedAt()).toSeconds() : null;
			return new RunRow(run.getStartedAt().atZone(zone), run.getStatus(), run.getListingsSucceeded(),
					run.getListingsFailed(), seconds);
		}

	}

}
