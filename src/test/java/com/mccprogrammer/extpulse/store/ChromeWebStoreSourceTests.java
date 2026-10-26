package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChromeWebStoreSourceTests {

	private static final String DIRECTORS_CUT = "kpjlaobgikkhpngdkladodopdkigmdei";

	private static final String UNIFIED_INBOX = "dmdmnlelljolpkpldndebhpkaomdimil";

	@RegisterExtension
	static WireMockExtension chromeWebStore = StoreTestSupport.wireMock();

	private ChromeWebStoreSource source;

	@BeforeEach
	void createSource() {
		this.source = new ChromeWebStoreSource(StoreTestSupport.propertiesFor(chromeWebStore));
	}

	@Test
	void readsUsersRatingAndVersionFromThePage() {
		stubPage(DIRECTORS_CUT, "chrome-web-store/" + DIRECTORS_CUT + ".html");

		assertThat(this.source.fetchStats(DIRECTORS_CUT))
			.isEqualTo(new ListingStats(8, new BigDecimal("5.00"), 2, "1.0.1"));
	}

	@Test
	void anItemWithoutRatingsHasNoAverage() {
		// The page of an unrated item has another item's rating further down, among
		// the related items; it must not be taken for this one's.
		stubPage(UNIFIED_INBOX, "chrome-web-store/" + UNIFIED_INBOX + ".html");

		assertThat(this.source.fetchStats(UNIFIED_INBOX)).isEqualTo(new ListingStats(37, null, 0, "1.0.2"));
	}

	@Test
	void anUnknownItemIsNotFound() {
		String unknown = "a".repeat(32);
		stubPage(unknown, "chrome-web-store/not-found.html");

		assertThatThrownBy(() -> this.source.fetchStats(unknown)).isInstanceOf(ListingNotFoundException.class);
	}

	@Test
	void aChangedPageIsAStoreExceptionNotAZero() {
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + DIRECTORS_CUT))
			.willReturn(aResponse().withHeader("Content-Type", "text/html")
				.withBody("<html><body><h1>Director's Cut for Letterboxd</h1><p>A new layout</p></body></html>")));

		assertThatThrownBy(() -> this.source.fetchStats(DIRECTORS_CUT))
			.isInstanceOf(StoreException.class)
			.isNotInstanceOf(ListingNotFoundException.class)
			.hasMessageContaining("has the page changed?");
	}

	@Test
	void rejectsSomethingThatIsNotAnItemId() {
		assertThatThrownBy(() -> this.source.fetchStats("../admin")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void followsTheRedirectToThePageWithTheItemsName() {
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + DIRECTORS_CUT)).willReturn(aResponse().withStatus(301)
			.withHeader("Location", "/detail/directors-cut-for-letter/" + DIRECTORS_CUT + "?hl=en")));
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/directors-cut-for-letter/" + DIRECTORS_CUT))
			.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8")
				.withBodyFile("chrome-web-store/" + DIRECTORS_CUT + ".html")));

		assertThat(this.source.fetchStats(DIRECTORS_CUT).installs()).isEqualTo(8);
	}

	@Test
	void stopsFollowingRedirectsAfterAFew() {
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + DIRECTORS_CUT)).willReturn(aResponse().withStatus(301)
			.withHeader("Location", "/detail/" + DIRECTORS_CUT + "?hl=en")));

		assertThatThrownBy(() -> this.source.fetchStats(DIRECTORS_CUT)).isInstanceOf(StoreException.class)
			.hasMessageContaining("redirected more than");
	}

	@Test
	void aLocationWithRawUtf8IsPercentEncoded() {
		// What the JDK hands over for "director’s-cut": the three UTF-8 bytes of ’ read as ISO-8859-1.
		String asReceived = new String("/detail/director’s-cut-for-letter/x?hl=en".getBytes(StandardCharsets.UTF_8),
				StandardCharsets.ISO_8859_1);

		assertThat(ChromeWebStoreSource.redirectTarget(URI.create("https://chromewebstore.google.com/detail/x"),
				asReceived))
			.isEqualTo(URI.create("https://chromewebstore.google.com/detail/director%E2%80%99s-cut-for-letter/x?hl=en"));
	}

	@Test
	void aLocationAlreadyEncodedIsKeptAsItIs() {
		String consent = "https://consent.google.com/m?continue=https://chromewebstore.google.com/detail/x?hl%3Den&gl=PT";

		assertThat(ChromeWebStoreSource.redirectTarget(URI.create("https://chromewebstore.google.com/detail/x"),
				consent))
			.isEqualTo(URI.create(consent));
	}

	@Test
	void doesNotReadReviewsYet() {
		assertThat(this.source.fetchReviews(DIRECTORS_CUT)).isEmpty();
	}

	@Test
	void cannotDiscoverByPublisher() {
		assertThat(this.source.discover("MCCProgrammer")).isEmpty();
	}

	private void stubPage(String itemId, String bodyFile) {
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + itemId))
			.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8").withBodyFile(bodyFile)));
	}

}
