package com.mccprogrammer.extpulse.publicpage;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeTests {

	@Test
	void numbersAreShortenedLikeOtherBadges() {
		assertThat(Badge.compact(0)).isEqualTo("0");
		assertThat(Badge.compact(999)).isEqualTo("999");
		assertThat(Badge.compact(1_000)).isEqualTo("1k");
		assertThat(Badge.compact(12_345)).isEqualTo("12.3k");
		assertThat(Badge.compact(999_949)).isEqualTo("999.9k");
		assertThat(Badge.compact(999_950)).isEqualTo("1M");
		assertThat(Badge.compact(4_560_000)).isEqualTo("4.6M");
	}

	@Test
	void coordinatesUseADotWhateverTheServersLanguage() {
		Locale before = Locale.getDefault();
		try {
			Locale.setDefault(Locale.of("pt", "PT"));
			assertThat(Badge.svg("installs", "12.3k")).contains("x=\"29.5\"").doesNotContain(",5\"");
		}
		finally {
			Locale.setDefault(before);
		}
	}

	@Test
	void textIsEscaped() {
		assertThat(Badge.svg("a<b", "\"&\"")).contains("a&lt;b").contains("&quot;&amp;&quot;").doesNotContain("a<b");
	}

	@Test
	void widerTextMakesAWiderBadge() {
		assertThat(Badge.textWidth("installs")).isLessThan(Badge.textWidth("downloads"));
		assertThat(Badge.textWidth("MW")).isGreaterThan(Badge.textWidth("il"));
	}

}
