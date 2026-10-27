package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketplaceSourceTests {

	private static final String QUERY = "/_apis/public/gallery/extensionquery";

	@RegisterExtension
	static WireMockExtension marketplace = StoreTestSupport.wireMock();

	private MarketplaceSource source;

	@BeforeEach
	void createSource() {
		this.source = new MarketplaceSource(StoreTestSupport.propertiesFor(marketplace));
	}

	@Test
	void discoversEveryExtensionOfAPublisher() {
		stubQuery(18, "MCCProgrammer", "marketplace/publisher-MCCProgrammer.json");

		assertThat(this.source.discover("MCCProgrammer")).hasSize(5)
			.contains(new DiscoveredListing(Store.VS_MARKETPLACE, "MCCProgrammer.diff-checker",
					"mccprogrammer.diff-checker", "Diff Checker",
					marketplace.baseUrl() + "/items?itemName=MCCProgrammer.diff-checker"));
	}

	@Test
	void anUnknownPublisherHasNoListings() {
		stubQuery(18, "NoSuchPublisher", "marketplace/not-found.json");

		assertThat(this.source.discover("NoSuchPublisher")).isEmpty();
	}

	@Test
	void readsInstallsRatingAndVersion() {
		stubQuery(7, "MCCProgrammer.diff-checker", "marketplace/diff-checker.json");

		assertThat(this.source.fetchStats("MCCProgrammer.diff-checker"))
			.isEqualTo(new ListingStats(1764, new BigDecimal("1.00"), 1, "0.3.0"));
	}

	@Test
	void anExtensionWithoutRatingsHasNoAverage() {
		stubQuery(7, "MCCProgrammer.debug-dataStructures-visualizer-extension",
				"marketplace/debug-dataStructures-visualizer-extension.json");

		assertThat(this.source.fetchStats("MCCProgrammer.debug-dataStructures-visualizer-extension"))
			.isEqualTo(new ListingStats(16217, null, 0, "2.0.0"));
	}

	@Test
	void anUnknownExtensionIsNotFound() {
		stubQuery(7, "MCCProgrammer.does-not-exist", "marketplace/not-found.json");

		assertThatThrownBy(() -> this.source.fetchStats("MCCProgrammer.does-not-exist"))
			.isInstanceOf(ListingNotFoundException.class);
	}

	@Test
	void sendsThePinnedApiVersionAndAsksOnlyForTheLatestVersion() {
		stubQuery(7, "MCCProgrammer.diff-checker", "marketplace/diff-checker.json");

		this.source.fetchStats("MCCProgrammer.diff-checker");

		marketplace.verify(postRequestedFor(urlEqualTo(QUERY))
			.withHeader("Accept", equalTo("application/json;api-version=7.2-preview.1"))
			.withHeader("User-Agent", containing("ExtPulse"))
			.withRequestBody(matchingJsonPath("$.flags", equalTo("769"))));
	}

	@Test
	void aServerErrorIsAStoreException() {
		marketplace.stubFor(post(QUERY).willReturn(aResponse().withStatus(500)));

		assertThatThrownBy(() -> this.source.fetchStats("MCCProgrammer.diff-checker"))
			.isInstanceOf(StoreException.class)
			.isNotInstanceOf(ListingNotFoundException.class);
	}

	@Test
	void aDroppedConnectionIsTriedOnceMore() {
		marketplace.stubFor(post(QUERY).inScenario("dropped")
			.whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
			.willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER))
			.willSetStateTo("reset once"));
		marketplace.stubFor(post(QUERY).inScenario("dropped")
			.whenScenarioStateIs("reset once")
			.willReturn(aResponse().withHeader("Content-Type", "application/json")
				.withBodyFile("marketplace/diff-checker.json")));

		assertThat(this.source.fetchStats("MCCProgrammer.diff-checker").installs()).isEqualTo(1764);
		marketplace.verify(2, postRequestedFor(urlEqualTo(QUERY)));
	}

	@Test
	void aConnectionDroppedTwiceIsAStoreException() {
		marketplace.stubFor(post(QUERY)
			.willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

		assertThatThrownBy(() -> this.source.fetchStats("MCCProgrammer.diff-checker"))
			.isInstanceOf(StoreException.class);
		marketplace.verify(2, postRequestedFor(urlEqualTo(QUERY)));
	}

	@Test
	void aSlowAnswerTimesOut() {
		marketplace.stubFor(post(QUERY).willReturn(aResponse()
			.withFixedDelay((int) StoreTestSupport.READ_TIMEOUT.toMillis() * 4)
			.withBodyFile("marketplace/diff-checker.json")));

		assertThatThrownBy(() -> this.source.fetchStats("MCCProgrammer.diff-checker"))
			.isInstanceOf(StoreException.class);
	}

	@Test
	void readsReviewsWithThePublishersReply() {
		stubReviews("MCCProgrammer", "diff-checker", aResponse().withBodyFile("marketplace/reviews-diff-checker.json"));

		List<StoreReview> reviews = this.source.fetchReviews("MCCProgrammer.diff-checker");

		assertThat(reviews).singleElement().satisfies((review) -> {
			assertThat(review.externalId()).isEqualTo("326941");
			assertThat(review.rating()).isEqualTo(1);
			assertThat(review.text()).startsWith("Does not work for me.");
			assertThat(review.author()).isEqualTo("Reviewer 1");
			assertThat(review.productVersion()).isEqualTo("0.1.1");
			assertThat(review.postedAt()).isEqualTo(Instant.parse("2026-01-29T15:11:13.57Z"));
			assertThat(review.reply()).startsWith("Hi,");
		});
	}

	@Test
	void keepsRatingsWithoutTextAndDoesNotAskTheStoreToHideThem() {
		stubReviews("esbenp", "prettier-vscode", aResponse().withBodyFile("marketplace/reviews-prettier-vscode.json"));

		List<StoreReview> reviews = this.source.fetchReviews("esbenp.prettier-vscode");

		assertThat(reviews).hasSize(5);
		assertThat(reviews).filteredOn((review) -> review.text() == null)
			.singleElement()
			.satisfies((review) -> assertThat(review.rating()).isEqualTo(5));
		// filterOptions=1 would leave ratings without text out.
		marketplace.verify(getRequestedFor(urlPathEqualTo("/_apis/public/gallery/publishers/esbenp/extensions/prettier-vscode/reviews"))
			.withQueryParam("filterOptions", absent())
			.withHeader("Accept", equalTo("application/json;api-version=7.2-preview.1")));
	}

	@Test
	void leavesOutDeletedAndHiddenReviewsAndDeletedReplies() {
		stubReviews("MCCProgrammer", "diff-checker", aResponse().withBody("""
				{"reviews": [
				  {"id": 1, "userDisplayName": "A", "updatedDate": "2026-10-01T10:00:00Z", "rating": 4, "text": "ok",
				   "isDeleted": false, "isIgnored": false, "reply": {"replyText": "thanks", "isDeleted": true}},
				  {"id": 2, "userDisplayName": "B", "updatedDate": "2026-10-02T10:00:00Z", "rating": 1, "text": "x",
				   "isDeleted": true, "isIgnored": false},
				  {"id": 3, "userDisplayName": "C", "updatedDate": "2026-10-03T10:00:00Z", "rating": 1, "text": "spam",
				   "isDeleted": false, "isIgnored": true}
				]}"""));

		assertThat(this.source.fetchReviews("MCCProgrammer.diff-checker")).singleElement()
			.satisfies((review) -> assertThat(review.reply()).isNull());
	}

	@Test
	void reviewsOfAnUnknownExtensionAreNotFound() {
		stubReviews("MCCProgrammer", "gone", aResponse().withStatus(404));

		assertThatThrownBy(() -> this.source.fetchReviews("MCCProgrammer.gone"))
			.isInstanceOf(ListingNotFoundException.class);
		assertThatThrownBy(() -> this.source.fetchReviews("no-dot")).isInstanceOf(IllegalArgumentException.class);
	}

	private void stubReviews(String publisher, String name,
			com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
		marketplace.stubFor(get(urlPathEqualTo("/_apis/public/gallery/publishers/" + publisher + "/extensions/" + name
				+ "/reviews"))
			.willReturn(response.withHeader("Content-Type", "application/json; charset=utf-8")));
	}

	private void stubQuery(int filterType, String value, String bodyFile) {
		marketplace.stubFor(post(QUERY)
			.withRequestBody(matchingJsonPath("$.filters[0].criteria[0].filterType", equalTo(String.valueOf(filterType))))
			.withRequestBody(matchingJsonPath("$.filters[0].criteria[0].value", equalTo(value)))
			.willReturn(aResponse().withHeader("Content-Type", "application/json; charset=utf-8")
				.withBodyFile(bodyFile)));
	}

}
