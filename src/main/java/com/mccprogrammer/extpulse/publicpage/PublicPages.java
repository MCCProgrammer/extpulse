package com.mccprogrammer.extpulse.publicpage;

import java.time.Duration;
import java.util.List;

import com.mccprogrammer.extpulse.auth.PublicProfiles;
import com.mccprogrammer.extpulse.stats.StatsService;
import com.mccprogrammer.extpulse.stats.StatsService.ExtensionOverview;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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
 * <p>
 * Each badge also comes as JSON for shields.io's endpoint badges
 * ({@code https://img.shields.io/endpoint?url=...}). The VS Code Marketplace only shows
 * badges from services it knows, shields.io among them, so that is the one to put in an
 * extension's README.
 * <p>
 * The exact numbers also come as JSON that any website may read from the browser, so a
 * portfolio can show live installs in its own style.
 */
@Controller
class PublicPages {

	private static final MediaType SVG = MediaType.valueOf("image/svg+xml");

	private static final Duration CACHE_SECONDS = Duration.ofHours(1);

	private static final CacheControl CACHE = CacheControl.maxAge(CACHE_SECONDS).cachePublic();

	// Public, read-only and without cookies, so any origin may read it.
	private static final String ANY_ORIGIN = "*";

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
		return badge(extensionInstalls(slug, id));
	}

	/** {@link #totalBadge} for shields.io. */
	@GetMapping("/p/{slug}/badge.json")
	ResponseEntity<ShieldsBadge> totalShieldsBadge(@PathVariable String slug) {
		long installs = this.stats.overview(owner(slug)).stream().mapToLong(ExtensionOverview::installs).sum();
		return shieldsBadge(installs);
	}

	/** {@link #extensionBadge} for shields.io. */
	@GetMapping("/p/{slug}/extensions/{id}/badge.json")
	ResponseEntity<ShieldsBadge> extensionShieldsBadge(@PathVariable String slug, @PathVariable long id) {
		return shieldsBadge(extensionInstalls(slug, id));
	}

	/** Installs of the page's extensions and their total, as exact numbers. */
	@GetMapping("/p/{slug}/installs.json")
	ResponseEntity<PublicInstalls> installs(@PathVariable String slug) {
		List<ExtensionOverview> extensions = this.stats.overview(owner(slug));
		List<ExtensionInstalls> each = extensions.stream()
			.map((extension) -> new ExtensionInstalls(extension.id(), extension.displayName(), extension.installs()))
			.toList();
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_JSON)
			.cacheControl(CACHE)
			.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ANY_ORIGIN)
			.body(new PublicInstalls(each.stream().mapToLong(ExtensionInstalls::installs).sum(), each));
	}

	private long extensionInstalls(String slug, long id) {
		return this.stats.overview(owner(slug))
			.stream()
			.filter((candidate) -> candidate.id() == id)
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))
			.installs();
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

	private static ResponseEntity<ShieldsBadge> shieldsBadge(long installs) {
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_JSON)
			.cacheControl(CACHE)
			.body(new ShieldsBadge(1, "installs", Badge.compact(installs), Badge.COLOR,
					(int) CACHE_SECONDS.toSeconds()));
	}

	/**
	 * shields.io's endpoint schema. {@code cacheSeconds} asks shields.io to keep it as
	 * long as this site lets anyone cache it.
	 */
	record ShieldsBadge(int schemaVersion, String label, String message, String color, int cacheSeconds) {
	}

	record PublicInstalls(long installs, List<ExtensionInstalls> extensions) {
	}

	record ExtensionInstalls(long id, String name, long installs) {
	}

}
