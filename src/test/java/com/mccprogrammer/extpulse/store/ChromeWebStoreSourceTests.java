package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChromeWebStoreSourceTests {

	private static final String DIRECTORS_CUT = "kpjlaobgikkhpngdkladodopdkigmdei";

	private static final String UNIFIED_INBOX = "dmdmnlelljolpkpldndebhpkaomdimil";

	// AdBlock: many reviews, in English and Portuguese, most with the developer's reply.
	private static final String ADBLOCK = "gighmmpiobklfepjocnamgkkbiglidom";

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
		// The page of an unrated item has another item's rating further down, among the
		// related items; it must not be taken for this one's.
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

		assertThatThrownBy(() -> this.source.fetchStats(DIRECTORS_CUT)).isInstanceOf(StoreException.class)
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
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + DIRECTORS_CUT))
			.willReturn(aResponse().withStatus(301).withHeader("Location", "/detail/" + DIRECTORS_CUT + "?hl=en")));

		assertThatThrownBy(() -> this.source.fetchStats(DIRECTORS_CUT)).isInstanceOf(StoreException.class)
			.hasMessageContaining("redirected more than");
	}

	@Test
	void aLocationWithRawUtf8IsPercentEncoded() {
		// What the JDK hands over for "director’s-cut": the three UTF-8 bytes of ’ read
		// as ISO-8859-1.
		String asReceived = new String("/detail/director’s-cut-for-letter/x?hl=en".getBytes(StandardCharsets.UTF_8),
				StandardCharsets.ISO_8859_1);

		assertThat(ChromeWebStoreSource
			.redirectTarget(URI.create("https://chromewebstore.google.com/detail/x"), asReceived)).isEqualTo(
					URI.create("https://chromewebstore.google.com/detail/director%E2%80%99s-cut-for-letter/x?hl=en"));
	}

	@Test
	void aLocationAlreadyEncodedIsKeptAsItIs() {
		String consent = "https://consent.google.com/m?continue=https://chromewebstore.google.com/detail/x?hl%3Den&gl=PT";

		assertThat(
				ChromeWebStoreSource.redirectTarget(URI.create("https://chromewebstore.google.com/detail/x"), consent))
			.isEqualTo(URI.create(consent));
	}

	@Test
	void readsTheReviewsOfEveryConfiguredLanguageNewestFirst() {
		stubReviews(ADBLOCK, "en", "chrome-web-store/reviews-" + ADBLOCK + "-en.html");
		stubReviews(ADBLOCK, "pt", "chrome-web-store/reviews-" + ADBLOCK + "-pt.html");

		List<StoreReview> reviews = this.source.fetchReviews(ADBLOCK);

		assertThat(reviews).hasSize(20);
		assertThat(reviews).extracting(StoreReview::postedAt).isSortedAccordingTo((a, b) -> b.compareTo(a));
		assertThat(reviews.getFirst())
			.isEqualTo(new StoreReview("4b33cd1e-346c-445d-8f66-4b14f6f1a6d7", 1, reviews.getFirst().text(),
					"Reviewer 1", "6.51.0", Instant.parse("2026-10-28T09:09:18.918956273Z"), null));
		assertThat(reviews.getFirst().text()).startsWith("I have been using this for years");
		StoreReview oldest = reviews.getLast();
		assertThat(oldest.externalId()).isEqualTo("48f39326-8edb-40bf-bbe4-aa7ccf859587");
		assertThat(oldest.rating()).isEqualTo(3);
		assertThat(oldest.text()).startsWith("Agora estão cobrando");
		assertThat(oldest.reply()).startsWith("O AdBlock é gratuito");
		assertThat(reviews).filteredOn((review) -> review.reply() != null).hasSize(17);
	}

	@Test
	void aReviewListedInTwoLanguagesIsReadOnce() {
		stubReviews(ADBLOCK, "en", "chrome-web-store/reviews-" + ADBLOCK + "-en.html");
		stubReviews(ADBLOCK, "pt", "chrome-web-store/reviews-" + ADBLOCK + "-en.html");

		assertThat(this.source.fetchReviews(ADBLOCK)).hasSize(10);
	}

	@Test
	void ratingsWithoutACommentAreNotReviews() {
		// Director's Cut has two 5-star ratings and no written review.
		stubReviews(DIRECTORS_CUT, "en", "chrome-web-store/reviews-" + DIRECTORS_CUT + "-en.html");
		stubReviews(DIRECTORS_CUT, "pt", "chrome-web-store/reviews-" + DIRECTORS_CUT + "-en.html");

		assertThat(this.source.fetchReviews(DIRECTORS_CUT)).isEmpty();
	}

	@Test
	void theReviewsOfAnUnknownItemAreNotFound() {
		String unknown = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
		stubReviews(unknown, "en", "chrome-web-store/reviews-not-found.html");

		assertThatThrownBy(() -> this.source.fetchReviews(unknown)).isInstanceOf(ListingNotFoundException.class);
	}

	@Test
	void aReviewsPageWithoutItsDataIsAStoreException() {
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + ADBLOCK + "/reviews"))
			.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8")
				.withBody("<html><body><h1>AdBlock</h1><script>var x = 1;</script></body></html>")));

		assertThatThrownBy(() -> this.source.fetchReviews(ADBLOCK)).isInstanceOf(StoreException.class)
			.isNotInstanceOf(ListingNotFoundException.class)
			.hasMessageContaining("has the page changed?");
	}

	@Test
	void aReviewThatIsNotAsExpectedIsAStoreException() {
		String page = "<html><body><h1>AdBlock</h1>"
				+ "<script>var AF_dataServiceRequests = {'ds:1' : {id:'x1DgCd',request:[\"" + ADBLOCK
				+ "\",[10],2,null,null,null,0]}};</script>"
				+ "<script>AF_initDataCallback({key: 'ds:1', hash: '1', data:[null,[[\"id\",[\"Someone\",null],9,\"text\","
				+ "[1793124402,0],[1793124402,0]]],1], sideChannel: {}});</script></body></html>";
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + ADBLOCK + "/reviews"))
			.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8").withBody(page)));

		assertThatThrownBy(() -> this.source.fetchReviews(ADBLOCK)).isInstanceOf(StoreException.class)
			.hasMessageContaining("isn't as expected");
	}

	@Test
	void unreadableReviewsDataIsAStoreException() {
		String page = "<html><body><h1>AdBlock</h1>"
				+ "<script>var AF_dataServiceRequests = {'ds:1' : {id:'x1DgCd',request:[\"" + ADBLOCK
				+ "\",[10],2]}};</script>"
				+ "<script>AF_initDataCallback({key: 'ds:1', hash: '1', data:[null,[[, sideChannel: {}});</script></body></html>";
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + ADBLOCK + "/reviews"))
			.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8").withBody(page)));

		assertThatThrownBy(() -> this.source.fetchReviews(ADBLOCK)).isInstanceOf(StoreException.class)
			.hasMessageContaining("unreadable reviews");
	}

	@Test
	void rejectsReviewsOfSomethingThatIsNotAnItemId() {
		assertThatThrownBy(() -> this.source.fetchReviews("../admin")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void cannotDiscoverByPublisher() {
		assertThat(this.source.discover("MCCProgrammer")).isEmpty();
	}

	private void stubReviews(String itemId, String language, String bodyFile) {
		chromeWebStore
			.stubFor(get(urlPathEqualTo("/detail/" + itemId + "/reviews")).withQueryParam("hl", equalTo(language))
				.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8").withBodyFile(bodyFile)));
	}

	private void stubPage(String itemId, String bodyFile) {
		chromeWebStore.stubFor(get(urlPathEqualTo("/detail/" + itemId))
			.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8").withBodyFile(bodyFile)));
	}

}
