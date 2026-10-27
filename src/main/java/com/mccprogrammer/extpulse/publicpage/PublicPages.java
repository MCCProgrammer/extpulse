package com.mccprogrammer.extpulse.publicpage;

import java.time.Duration;
import java.util.List;

import com.mccprogrammer.extpulse.auth.PublicProfiles;
import com.mccprogrammer.extpulse.stats.StatsService;
import com.mccprogrammer.extpulse.stats.StatsService.ExtensionOverview;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * What anyone can see without signing in: the home page, and for accounts that chose an
 * address, a portfolio page and installs badges. Numbers change once a day, so badges may
 * be cached for an hour (GitHub's image proxy does).
 */
@Controller
class PublicPages {

	private static final MediaType SVG = MediaType.valueOf("image/svg+xml");

	private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

	private final PublicProfiles profiles;

	private final StatsService stats;

	PublicPages(PublicProfiles profiles, StatsService stats) {
		this.profiles = profiles;
		this.stats = stats;
	}

	@GetMapping("/")
	String home() {
		return "public/home";
	}

	@GetMapping("/p/{slug}")
	String portfolio(@PathVariable String slug, Model model) {
		List<ExtensionOverview> extensions = this.stats.overview(owner(slug));
		model.addAttribute("slug", slug);
		model.addAttribute("extensions", extensions);
		model.addAttribute("installs", extensions.stream().mapToLong(ExtensionOverview::installs).sum());
		return "public/portfolio";
	}

	/** Installs of every extension on the page, across stores. */
	@GetMapping("/p/{slug}/badge.svg")
	ResponseEntity<String> totalBadge(@PathVariable String slug) {
		long installs = this.stats.overview(owner(slug)).stream().mapToLong(ExtensionOverview::installs).sum();
		return badge(installs);
	}

	/** Installs of one extension, across the stores it is on. */
	@GetMapping("/p/{slug}/extensions/{id}/badge.svg")
	ResponseEntity<String> extensionBadge(@PathVariable String slug, @PathVariable long id) {
		ExtensionOverview extension = this.stats.overview(owner(slug))
			.stream()
			.filter((candidate) -> candidate.id() == id)
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return badge(extension.installs());
	}

	private long owner(String slug) {
		return this.profiles.ownerOf(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static ResponseEntity<String> badge(long installs) {
		return ResponseEntity.ok()
			.contentType(SVG)
			.cacheControl(CACHE)
			.body(Badge.svg("installs", Badge.compact(installs)));
	}

}
