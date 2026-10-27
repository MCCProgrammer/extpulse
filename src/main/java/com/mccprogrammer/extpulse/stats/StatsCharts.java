package com.mccprogrammer.extpulse.stats;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mccprogrammer.extpulse.stats.StatsService.Point;

/**
 * The geometry of the extension page's two charts, drawn as SVG by the template: installs
 * over time (a line) and the gain of each day (columns from a zero baseline). Everything
 * is computed here, so the page needs no JavaScript and the shapes can be tested.
 */
final class StatsCharts {

	static final int WIDTH = 720;

	static final int HEIGHT = 220;

	private static final int LEFT = 72;

	private static final int RIGHT = 16;

	private static final int TOP = 16;

	private static final int BOTTOM = 28;

	private static final int Y_TICKS = 4;

	private static final double MAX_BAR = 24;

	private static final double BAR_RADIUS = 4;

	/** Where the plot sits in the SVG, for the template's axes and gridlines. */
	static final Frame FRAME = new Frame(WIDTH, HEIGHT, LEFT, WIDTH - RIGHT, TOP, HEIGHT - BOTTOM);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.UK);

	private StatsCharts() {
	}

	/** Installs over time; empty with fewer than two points, which make no line. */
	static LineChart installs(List<Point> points) {
		if (points.size() < 2) {
			return LineChart.EMPTY;
		}
		long min = points.stream().mapToLong(Point::installs).min().orElseThrow();
		long max = points.stream().mapToLong(Point::installs).max().orElseThrow();
		// Installs are large and change little day to day: the scale starts near the
		// lowest value, not at zero, or the line would be flat. A line (not an area)
		// doesn't claim a zero baseline.
		Scale scale = Scale.of(min, max, false);
		List<Hit> hits = new ArrayList<>();
		StringBuilder path = new StringBuilder();
		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			double x = x(i, points.size());
			double y = scale.y(point.installs());
			path.append((i == 0) ? "M" : "L").append(round(x)).append(' ').append(round(y));
			String gain = (point.gain() != null) ? " (" + signed(point.gain()) + ")" : "";
			hits.add(new Hit(round(x - slot(points.size()) / 2), round(slot(points.size())), round(x), round(y),
					DAY.format(point.date()) + ": " + grouped(point.installs()) + " installs" + gain));
		}
		Hit last = hits.getLast();
		// The latest value is labelled above its dot, or below it when the dot is at the
		// top.
		double labelY = (last.y() - TOP < 20) ? last.y() + 20 : last.y() - 10;
		return new LineChart(true, path.toString(), scale.ticks(), xTicks(points), hits, last.x(), last.y(),
				round(labelY), grouped(points.getLast().installs()));
	}

	/**
	 * The gain of each day, as columns up (gains) or down (stores also count uninstalls).
	 */
	static ColumnChart gains(List<Point> points) {
		List<Point> known = points.stream().filter((point) -> point.gain() != null).toList();
		if (known.isEmpty()) {
			return ColumnChart.EMPTY;
		}
		long min = Math.min(0, known.stream().mapToLong(Point::gain).min().orElseThrow());
		long max = Math.max(0, known.stream().mapToLong(Point::gain).max().orElseThrow());
		Scale scale = Scale.of(min, max, true);
		double zero = scale.y(0);
		double width = Math.min(MAX_BAR, slot(points.size()) * 0.6);
		List<Column> columns = new ArrayList<>();
		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			if (point.gain() == null) {
				continue;
			}
			double x = x(i, points.size());
			double end = scale.y(point.gain());
			columns.add(new Column(columnPath(x - width / 2, width, zero, end), round(x - slot(points.size()) / 2),
					round(slot(points.size())), DAY.format(point.date()) + ": " + signed(point.gain()) + " installs"));
		}
		return new ColumnChart(true, columns, scale.ticks(), xTicks(points), round(zero));
	}

	/**
	 * A column with a 4px rounded end at its value and a square end on the baseline; a
	 * zero gain is a hairline on the baseline.
	 */
	static String columnPath(double left, double width, double baseline, double end) {
		double height = Math.abs(baseline - end);
		double r = Math.min(BAR_RADIUS, Math.min(height, width / 2));
		double right = left + width;
		if (height < 1) {
			return "M" + round(left) + " " + round(baseline - 0.5) + "H" + round(right) + "V" + round(baseline + 0.5)
					+ "H" + round(left) + "Z";
		}
		// Up: the rounded end is at the top (smaller y); down: at the bottom.
		double direction = (end < baseline) ? 1 : -1;
		return "M" + round(left) + " " + round(baseline) + "V" + round(end + direction * r) + "Q" + round(left) + " "
				+ round(end) + " " + round(left + r) + " " + round(end) + "H" + round(right - r) + "Q" + round(right)
				+ " " + round(end) + " " + round(right) + " " + round(end + direction * r) + "V" + round(baseline)
				+ "Z";
	}

	private static List<Tick> xTicks(List<Point> points) {
		// The first and last day, and the middle one when there is room between them.
		List<Integer> indexes = (points.size() > 4) ? List.of(0, (points.size() - 1) / 2, points.size() - 1)
				: List.of(0, points.size() - 1);
		return indexes.stream()
			.distinct()
			.map((i) -> new Tick(round(x(i, points.size())), DAY.format(points.get(i).date())))
			.toList();
	}

	/** The middle of the i-th of n slots across the plot. */
	private static double x(int i, int n) {
		return LEFT + slot(n) * (i + 0.5);
	}

	private static double slot(int n) {
		return (double) (WIDTH - LEFT - RIGHT) / n;
	}

	static String grouped(long value) {
		return String.format(Locale.UK, "%,d", value);
	}

	static String signed(long value) {
		return ((value > 0) ? "+" : "") + grouped(value);
	}

	private static double round(double value) {
		return Math.round(value * 10) / 10.0;
	}

	/** A linear scale over round tick values, from data values to SVG y. */
	record Scale(long low, long high, long step) {

		static Scale of(long min, long max, boolean fromZero) {
			long range = Math.max(max - min, 1);
			long step = niceStep(range / (double) Y_TICKS);
			long low = Math.floorDiv(min, step) * step;
			long high = Math.max(Math.ceilDiv(max, step) * step, low + step);
			if (fromZero) {
				low = Math.min(low, 0);
			}
			return new Scale(low, high, step);
		}

		/** 1, 2 or 5 times a power of ten: ticks people read at a glance. */
		static long niceStep(double rough) {
			long power = 1;
			while (power * 10 <= rough) {
				power *= 10;
			}
			for (long multiple : new long[] { 1, 2, 5, 10 }) {
				if (power * multiple >= rough) {
					return power * multiple;
				}
			}
			return power * 10;
		}

		double y(long value) {
			return TOP + (HEIGHT - TOP - BOTTOM) * (this.high - value) / (double) (this.high - this.low);
		}

		List<Tick> ticks() {
			List<Tick> ticks = new ArrayList<>();
			for (long value = this.low; value <= this.high; value += this.step) {
				ticks.add(new Tick(round(y(value)), grouped(value)));
			}
			return ticks;
		}

	}

	/** A position on an axis and its label. */
	record Tick(double at, String label) {
	}

	/**
	 * The area of one day that shows its tooltip, and the point it belongs to.
	 */
	record Hit(double left, double width, double x, double y, String title) {
	}

	record Column(String path, double left, double width, String title) {
	}

	record Frame(int width, int height, int left, int right, int top, int bottom) {
	}

	record LineChart(boolean drawn, String path, List<Tick> yTicks, List<Tick> xTicks, List<Hit> hits, double endX,
			double endY, double endLabelY, String endLabel) {

		static final LineChart EMPTY = new LineChart(false, "", List.of(), List.of(), List.of(), 0, 0, 0, "");

	}

	record ColumnChart(boolean drawn, List<Column> columns, List<Tick> yTicks, List<Tick> xTicks, double zeroY) {

		static final ColumnChart EMPTY = new ColumnChart(false, List.of(), List.of(), List.of(), 0);

	}

}
