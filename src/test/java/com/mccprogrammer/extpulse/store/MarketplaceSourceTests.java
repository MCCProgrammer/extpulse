package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
	void aSlowAnswerTimesOut() {
		marketplace.stubFor(post(QUERY).willReturn(aResponse()
			.withFixedDelay((int) StoreTestSupport.READ_TIMEOUT.toMillis() * 4)
			.withBodyFile("marketplace/diff-checker.json")));

		assertThatThrownBy(() -> this.source.fetchStats("MCCProgrammer.diff-checker"))
			.isInstanceOf(StoreException.class);
	}

	private void stubQuery(int filterType, String value, String bodyFile) {
		marketplace.stubFor(post(QUERY)
			.withRequestBody(matchingJsonPath("$.filters[0].criteria[0].filterType", equalTo(String.valueOf(filterType))))
			.withRequestBody(matchingJsonPath("$.filters[0].criteria[0].value", equalTo(value)))
			.willReturn(aResponse().withHeader("Content-Type", "application/json; charset=utf-8")
				.withBodyFile(bodyFile)));
	}

}
