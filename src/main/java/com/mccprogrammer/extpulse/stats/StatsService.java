package com.mccprogrammer.extpulse.stats;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mccprogrammer.extpulse.ApiProblem;
import com.mccprogrammer.extpulse.catalog.CatalogService;
import com.mccprogrammer.extpulse.catalog.Extension;
import com.mccprogrammer.extpulse.stats.StatsQueries.DayRow;
import com.mccprogrammer.extpulse.stats.StatsQueries.ListingRow;
import com.mccprogrammer.extpulse.store.Store;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class StatsService {

	static final int DEFAULT_DAYS = 30;

	static final int MAX_DAYS = 366;

	private final StatsQueries queries;

	private final CatalogService catalog;

	private final Clock clock;

	StatsService(StatsQueries queries, CatalogService catalog, Clock clock) {
		this.queries = queries;
		this.catalog = catalog;
		this.clock = clock;
	}

	/**
	 * Every extension of the user, with today's installs and the gain over the last 7
	 * days.
	 */
	List<ExtensionOverview> overview(long userId) {
		LocalDate today = LocalDate.now(this.clock);
		Map<Long, List<ListingRow>> byExtension = new LinkedHashMap<>();
		for (ListingRow row : this.queries.listingsOf(userId, today, today.minusDays(7))) {
			byExtension.computeIfAbsent(row.extensionId(), (id) -> new ArrayList<>()).add(row);
		}
		return byExtension.values()
			.stream()
			.map(StatsService::overviewOf)
			.sorted(Comparator.comparing(ExtensionOverview::installs)
				.reversed()
				.thenComparing(ExtensionOverview::displayName))
			.toList();
	}

	private static ExtensionOverview overviewOf(List<ListingRow> rows) {
		ListingRow first = rows.getFirst();
		List<ListingOverview> listings = rows.stream()
			.filter((row) -> row.listingId() != null)
			.map((row) -> new ListingOverview(row.listingId(), row.store(), row.externalId(), row.url(), row.installs(),
					difference(row.installs(), row.installsWeekBefore()), row.ratingAvg(), row.ratingCount(),
					row.version(), row.takenOn()))
			.toList();
		long installs = listings.stream().mapToLong((listing) -> orZero(listing.installs())).sum();
		return new ExtensionOverview(first.extensionId(), first.displayName(), first.publisher(), installs,
				sumOfKnown(listings.stream().map(ListingOverview::weekGain).toList()), listings);
	}

	/**
	 * The extension's installs over time, by day or by week (ISO weeks, Monday first).
	 * Defaults to the last 30 days.
	 */
	Series series(long userId, long extensionId, LocalDate from, LocalDate to, Granularity granularity) {
		Extension extension = this.catalog.requireOwnedExtension(userId, extensionId);
		LocalDate end = (to != null) ? to : LocalDate.now(this.clock);
		LocalDate start = (from != null) ? from : end.minusDays(DEFAULT_DAYS - 1);
		if (start.isAfter(end)) {
			throw ApiProblem.of(HttpStatus.BAD_REQUEST, "from (" + start + ") is after to (" + end + ")");
		}
		if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
			throw ApiProblem.of(HttpStatus.BAD_REQUEST, "At most " + MAX_DAYS + " days at a time");
		}
		List<Point> days = this.queries.dailySeries(extensionId, start, end)
			.stream()
			.map(StatsService::pointOf)
			.toList();
		List<Point> points = (granularity == Granularity.WEEK) ? byWeek(days) : days;
		return new Series(extension.getId(), extension.getDisplayName(), start, end, granularity, points);
	}

	private static Point pointOf(DayRow row) {
		Map<Store, Long> byStore = new EnumMap<>(Store.class);
		putIfKnown(byStore, Store.VS_MARKETPLACE, row.vsMarketplace());
		putIfKnown(byStore, Store.OPEN_VSX, row.openVsx());
		putIfKnown(byStore, Store.CHROME_WEB_STORE, row.chromeWebStore());
		return new Point(row.day(), row.installs(), byStore, row.gain());
	}

	/**
	 * A week is its Monday, the installs on its last day in range and the sum of its
	 * gains.
	 */
	private static List<Point> byWeek(List<Point> days) {
		Map<LocalDate, List<Point>> weeks = new LinkedHashMap<>();
		for (Point day : days) {
			LocalDate monday = day.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			weeks.computeIfAbsent(monday, (key) -> new ArrayList<>()).add(day);
		}
		return weeks.entrySet().stream().map((week) -> {
			Point last = week.getValue().getLast();
			Long gain = sumOfKnown(week.getValue().stream().map(Point::gain).toList());
			return new Point(week.getKey(), last.installs(), last.byStore(), gain);
		}).toList();
	}

	private static void putIfKnown(Map<Store, Long> byStore, Store store, Long installs) {
		if (installs != null) {
			byStore.put(store, installs);
		}
	}

	private static Long difference(Long now, Long before) {
		return (now != null && before != null) ? now - before : null;
	}

	/** The sum of the known values, or null when none is known. */
	private static Long sumOfKnown(List<Long> values) {
		return values.stream().filter((value) -> value != null).reduce(Long::sum).orElse(null);
	}

	private static long orZero(Long value) {
		return (value != null) ? value : 0;
	}

	enum Granularity {

		DAY, WEEK

	}

	/**
	 * @param weekGain installs gained in the last 7 days, over the listings with a
	 * snapshot from a week ago; {@code null} while there is no such snapshot
	 */
	record ExtensionOverview(long id, String displayName, String publisher, long installs, Long weekGain,
			List<ListingOverview> listings) {
	}

	/**
	 * @param lastCollectedOn the day of the snapshot these numbers come from
	 */
	record ListingOverview(long id, Store store, String externalId, String url, Long installs, Long weekGain,
			BigDecimal ratingAvg, Integer ratingCount, String version, LocalDate lastCollectedOn) {
	}

	record Series(long extensionId, String displayName, LocalDate from, LocalDate to, Granularity granularity,
			List<Point> points) {
	}

	/**
	 * @param gain installs gained since the previous point; can be negative, since stores
	 * also count uninstalls
	 */
	record Point(LocalDate date, long installs, Map<Store, Long> byStore, Long gain) {
	}

}
