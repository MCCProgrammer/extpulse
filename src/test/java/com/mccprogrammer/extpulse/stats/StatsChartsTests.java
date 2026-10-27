package com.mccprogrammer.extpulse.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.mccprogrammer.extpulse.stats.StatsCharts.ColumnChart;
import com.mccprogrammer.extpulse.stats.StatsCharts.LineChart;
import com.mccprogrammer.extpulse.stats.StatsCharts.Scale;
import com.mccprogrammer.extpulse.stats.StatsService.Point;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatsChartsTests {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

	@Test
	void ticksAreRoundNumbers() {
		assertThat(Scale.niceStep(0.3)).isEqualTo(1);
		assertThat(Scale.niceStep(3)).isEqualTo(5);
		assertThat(Scale.niceStep(25)).isEqualTo(50);
		assertThat(Scale.niceStep(120)).isEqualTo(200);
		assertThat(Scale.niceStep(1000)).isEqualTo(1000);
		assertThat(Scale.niceStep(7_000_000)).isEqualTo(10_000_000);

		// A range of 456 in about 4 steps: steps of 200, from below the lowest value to
		// above the highest.
		Scale scale = Scale.of(12_034, 12_490, false);
		assertThat(scale.low()).isEqualTo(12_000);
		assertThat(scale.high()).isEqualTo(12_600);
		assertThat(scale.ticks()).extracting(StatsCharts.Tick::label)
			.containsExactly("12,000", "12,200", "12,400", "12,600");
	}

	@Test
	void gainsAlwaysIncludeZero() {
		Scale scale = Scale.of(5, 40, true);
		assertThat(scale.low()).isZero();
		assertThat(scale.y(0)).isGreaterThan(scale.y(40));
	}

	@Test
	void oneDayMakesNoLine() {
		assertThat(StatsCharts.installs(List.of(point(0, 100, null))).drawn()).isFalse();
		assertThat(StatsCharts.gains(List.of(point(0, 100, null))).drawn()).isFalse();
	}

	@Test
	void theLineHasAPointPerDayAndLabelsItsEnd() {
		LineChart chart = StatsCharts.installs(List.of(point(0, 100, null), point(1, 110, 10L), point(2, 105, -5L)));

		assertThat(chart.drawn()).isTrue();
		assertThat(chart.path()).startsWith("M").containsOnlyOnce("M").contains("L");
		assertThat(chart.hits()).hasSize(3);
		assertThat(chart.hits().get(1).title()).isEqualTo("2 Oct: 110 installs (+10)");
		assertThat(chart.hits().get(2).title()).isEqualTo("3 Oct: 105 installs (-5)");
		assertThat(chart.endLabel()).isEqualTo("105");
		assertThat(chart.xTicks()).extracting(StatsCharts.Tick::label).containsExactly("1 Oct", "3 Oct");
	}

	@Test
	void theEndLabelMovesBelowADotAtTheTop() {
		LineChart rising = StatsCharts.installs(List.of(point(0, 100, null), point(1, 200, 100L)));
		assertThat(rising.endLabelY()).isGreaterThan(rising.endY());
		LineChart falling = StatsCharts.installs(List.of(point(0, 200, null), point(1, 100, -100L)));
		assertThat(falling.endLabelY()).isLessThan(falling.endY());
	}

	@Test
	void gainsGoUpAndLossesGoDownFromTheBaseline() {
		ColumnChart chart = StatsCharts
			.gains(List.of(point(0, 100, null), point(1, 110, 10L), point(2, 105, -5L), point(3, 105, 0L)));

		assertThat(chart.columns()).hasSize(3);
		assertThat(chart.columns().get(0).title()).isEqualTo("2 Oct: +10 installs");
		assertThat(chart.columns().get(1).title()).isEqualTo("3 Oct: -5 installs");
		assertThat(chart.zeroY()).isBetween((double) StatsCharts.FRAME.top(), (double) StatsCharts.FRAME.bottom());
	}

	@Test
	void columnsAreRoundedAtTheirValueAndSquareAtTheBaseline() {
		// Up from y=100 to y=60: the curves are near the top.
		assertThat(StatsCharts.columnPath(10, 20, 100, 60))
			.isEqualTo("M10.0 100.0V64.0Q10.0 60.0 14.0 60.0H26.0Q30.0 60.0 30.0 64.0V100.0Z");
		// Down from y=100 to y=130: the curves are near the bottom.
		assertThat(StatsCharts.columnPath(10, 20, 100, 130))
			.isEqualTo("M10.0 100.0V126.0Q10.0 130.0 14.0 130.0H26.0Q30.0 130.0 30.0 126.0V100.0Z");
		// Zero: a hairline on the baseline.
		assertThat(StatsCharts.columnPath(10, 20, 100, 100)).isEqualTo("M10.0 99.5H30.0V100.5H10.0Z");
	}

	private static Point point(int day, long installs, Long gain) {
		return new Point(DAY.plusDays(day), installs, Map.of(), gain);
	}

}
