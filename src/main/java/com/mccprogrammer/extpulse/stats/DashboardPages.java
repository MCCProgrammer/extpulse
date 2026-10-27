package com.mccprogrammer.extpulse.stats;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.mccprogrammer.extpulse.auth.PublicProfiles;
import com.mccprogrammer.extpulse.auth.SignedInUser;
import com.mccprogrammer.extpulse.stats.StatsService.ExtensionOverview;
import com.mccprogrammer.extpulse.stats.StatsService.Granularity;
import com.mccprogrammer.extpulse.stats.StatsService.Series;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The dashboard's home (every extension with its totals) and one extension's page (its
 * installs over time).
 */
@Controller
class DashboardPages {

	private static final Set<Integer> RANGES = Set.of(30, 90, 365);

	private final StatsService stats;

	private final PublicProfiles profiles;

	private final Clock clock;

	DashboardPages(StatsService stats, PublicProfiles profiles, Clock clock) {
		this.stats = stats;
		this.profiles = profiles;
		this.clock = clock;
	}

	@GetMapping("/app")
	String overview(@AuthenticationPrincipal SignedInUser user, Model model) {
		List<ExtensionOverview> extensions = this.stats.overview(user.id());
		model.addAttribute("extensions", extensions);
		model.addAttribute("installs", extensions.stream().mapToLong(ExtensionOverview::installs).sum());
		model.addAttribute("weekGain",
				extensions.stream()
					.map(ExtensionOverview::weekGain)
					.filter((gain) -> gain != null)
					.reduce(Long::sum)
					.orElse(null));
		return "stats/overview";
	}

	@GetMapping("/app/extensions/{id}")
	String extension(@AuthenticationPrincipal SignedInUser user, @PathVariable long id,
			@RequestParam(defaultValue = "30") int days, Model model) {
		int range = RANGES.contains(days) ? days : StatsService.DEFAULT_DAYS;
		LocalDate today = LocalDate.now(this.clock);
		Series series = this.stats.series(user.id(), id, today.minusDays(range - 1L), today, Granularity.DAY);
		model.addAttribute("series", series);
		model.addAttribute("days", range);
		model.addAttribute("overview",
				this.stats.overview(user.id())
					.stream()
					.filter((extension) -> extension.id() == id)
					.findFirst()
					.orElse(null));
		model.addAttribute("installsChart", StatsCharts.installs(series.points()));
		model.addAttribute("gainsChart", StatsCharts.gains(series.points()));
		model.addAttribute("frame", StatsCharts.FRAME);
		model.addAttribute("slug", this.profiles.slugOf(user.id()).orElse(null));
		return "stats/extension";
	}

}
