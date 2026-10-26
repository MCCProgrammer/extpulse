package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenVsxSourceTests {

	@RegisterExtension
	static WireMockExtension openVsx = StoreTestSupport.wireMock();

	private OpenVsxSource source;

	@BeforeEach
	void createSource() {
		this.source = new OpenVsxSource(StoreTestSupport.propertiesFor(openVsx));
	}

	@Test
	void discoversEveryExtensionOfANamespace() {
		stub("/api/MCCProgrammer", 200, "open-vsx/namespace-MCCProgrammer.json");
		for (String name : new String[] { "debug-dataStructures-visualizer-extension",
				"debug-sorting-algorithms-visualizer", "diff-checker", "github-style-markdown-preview", "markify" }) {
			stub("/api/MCCProgrammer/" + name, 200, "open-vsx/MCCProgrammer-" + name + ".json");
		}

		assertThat(this.source.discover("MCCProgrammer")).hasSize(5)
			.contains(new DiscoveredListing(Store.OPEN_VSX, "MCCProgrammer/diff-checker", "mccprogrammer.diff-checker",
					"Diff Checker",
					openVsx.baseUrl() + "/extension/MCCProgrammer/diff-checker"));
	}

	@Test
	void anUnknownNamespaceHasNoListings() {
		stub("/api/NoSuchPublisherXyz123", 404, "open-vsx/namespace-not-found.json");

		assertThat(this.source.discover("NoSuchPublisherXyz123")).isEmpty();
	}

	@Test
	void readsDownloadsAndVersionOfAnExtensionWithoutReviews() {
		stub("/api/MCCProgrammer/diff-checker", 200, "open-vsx/MCCProgrammer-diff-checker.json");

		assertThat(this.source.fetchStats("MCCProgrammer/diff-checker"))
			.isEqualTo(new ListingStats(1450, null, 0, "0.3.0"));
	}

	@Test
	void readsTheAverageRatingWhenThereAreReviews() {
		stub("/api/redhat/java", 200, "open-vsx/redhat-java.json");

		assertThat(this.source.fetchStats("redhat/java"))
			.isEqualTo(new ListingStats(46993751, new BigDecimal("5.00"), 16, "1.59.2026102408"));
	}

	@Test
	void anUnknownExtensionIsNotFound() {
		stub("/api/MCCProgrammer/does-not-exist", 404, "open-vsx/not-found.json");

		assertThatThrownBy(() -> this.source.fetchStats("MCCProgrammer/does-not-exist"))
			.isInstanceOf(ListingNotFoundException.class);
	}

	@Test
	void rejectsAnIdThatIsNotNamespaceSlashName() {
		assertThatThrownBy(() -> this.source.fetchStats("MCCProgrammer.diff-checker"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aServerErrorIsAStoreException() {
		openVsx.stubFor(get("/api/MCCProgrammer/diff-checker").willReturn(aResponse().withStatus(503)));

		assertThatThrownBy(() -> this.source.fetchStats("MCCProgrammer/diff-checker"))
			.isInstanceOf(StoreException.class)
			.isNotInstanceOf(ListingNotFoundException.class);
	}

	@Test
	void readsReviewsKeyedByTheirAuthor() {
		stub("/api/redhat/java/reviews", 200, "open-vsx/reviews-redhat-java.json");

		List<StoreReview> reviews = this.source.fetchReviews("redhat/java");

		assertThat(reviews).hasSize(16);
		assertThat(reviews.getFirst()).isEqualTo(new StoreReview("github:reviewer1", 5, null, "reviewer1", null,
				Instant.parse("2020-04-24T12:49:42.939089Z"), null));
		assertThat(reviews).filteredOn((review) -> review.text() != null).hasSize(3);
	}

	@Test
	void anExtensionWithoutReviewsHasNone() {
		stub("/api/MCCProgrammer/diff-checker/reviews", 200, "open-vsx/reviews-MCCProgrammer-diff-checker.json");

		assertThat(this.source.fetchReviews("MCCProgrammer/diff-checker")).isEmpty();
	}

	@Test
	void reviewsOfAnUnknownExtensionAreNotFound() {
		stub("/api/MCCProgrammer/does-not-exist/reviews", 404, "open-vsx/not-found.json");

		assertThatThrownBy(() -> this.source.fetchReviews("MCCProgrammer/does-not-exist"))
			.isInstanceOf(ListingNotFoundException.class);
	}

	private void stub(String path, int status, String bodyFile) {
		openVsx.stubFor(get(path).willReturn(aResponse().withStatus(status)
			.withHeader("Content-Type", "application/json")
			.withBodyFile(bodyFile)));
	}

}
