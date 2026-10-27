package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The VS Code Marketplace, through its {@code extensionquery} API. The API is public but
 * not officially documented, so the version it answers with is pinned. External IDs look
 * like {@code MCCProgrammer.diff-checker}.
 */
@Component
class MarketplaceSource implements StoreSource {

	private static final String API_VERSION = "application/json;api-version=7.2-preview.1";

	// Query flags: the latest version only, with its statistics.
	private static final int INCLUDE_VERSIONS = 0x1;

	private static final int INCLUDE_STATISTICS = 0x100;

	private static final int INCLUDE_LATEST_VERSION_ONLY = 0x200;

	private static final int FLAGS = INCLUDE_VERSIONS | INCLUDE_STATISTICS | INCLUDE_LATEST_VERSION_ONLY;

	// Filter types: one extension by "publisher.name", or every extension of a publisher.
	private static final int BY_EXTENSION_NAME = 7;

	private static final int BY_PUBLISHER_NAME = 18;

	// Enough for any publisher we know of; paging comes when one has more.
	private static final int PUBLISHER_PAGE_SIZE = 100;

	// The newest reviews first. The default filter: 1 would leave out ratings without
	// text.
	private static final int REVIEWS_PAGE_SIZE = 100;

	private final RestClient http;

	private final String baseUrl;

	MarketplaceSource(StoreProperties properties) {
		this.http = StoreHttp.client(properties.marketplaceUrl(), properties);
		this.baseUrl = properties.marketplaceUrl().toString();
	}

	@Override
	public Store store() {
		return Store.VS_MARKETPLACE;
	}

	@Override
	public List<DiscoveredListing> discover(String publisher) {
		return query(BY_PUBLISHER_NAME, publisher, PUBLISHER_PAGE_SIZE).stream().map(extension -> {
			String publisherName = extension.publisher().publisherName();
			String externalId = publisherName + "." + extension.extensionName();
			return new DiscoveredListing(store(), externalId,
					DiscoveredListing.vsCodeExtensionKey(publisherName, extension.extensionName()),
					extension.displayName(), this.baseUrl + "/items?itemName=" + externalId);
		}).toList();
	}

	@Override
	public ListingStats fetchStats(String externalId) {
		Extension extension = query(BY_EXTENSION_NAME, externalId, 1).stream()
			.findFirst()
			.orElseThrow(() -> new ListingNotFoundException(store(), externalId));
		// A statistic is missing, not zero, while nobody has rated or installed yet.
		BigDecimal ratingAvg = extension.statistic("averagerating") != null
				? BigDecimal.valueOf(extension.statistic("averagerating")) : null;
		return new ListingStats(extension.statisticOrZero("install"), ratingAvg,
				(int) extension.statisticOrZero("ratingcount"), extension.latestVersion());
	}

	@Override
	public List<StoreReview> fetchReviews(String externalId) {
		int dot = externalId.indexOf('.');
		if (dot <= 0 || dot == externalId.length() - 1) {
			throw new IllegalArgumentException("Not a Marketplace ID (publisher.name): " + externalId);
		}
		Reviews reviews;
		try {
			reviews = this.http.get()
				.uri("/_apis/public/gallery/publishers/{publisher}/extensions/{name}/reviews?count={count}",
						externalId.substring(0, dot), externalId.substring(dot + 1), REVIEWS_PAGE_SIZE)
				.header(HttpHeaders.ACCEPT, API_VERSION)
				.retrieve()
				.body(Reviews.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new ListingNotFoundException(store(), externalId);
		}
		catch (RestClientException ex) {
			throw new StoreException(store(), "reviews of " + externalId + " failed", ex);
		}
		if (reviews == null || reviews.reviews() == null) {
			return List.of();
		}
		return reviews.reviews()
			.stream()
			.filter((review) -> !review.isDeleted() && !review.isIgnored())
			.map((review) -> new StoreReview(String.valueOf(review.id()), review.rating(), blankToNull(review.text()),
					review.userDisplayName(), review.productVersion(), review.updatedDate(),
					(review.reply() != null && !review.reply().isDeleted()) ? review.reply().replyText() : null))
			.toList();
	}

	private static String blankToNull(String text) {
		return (text == null || text.isBlank()) ? null : text;
	}

	private List<Extension> query(int filterType, String value, int pageSize) {
		QueryRequest request = new QueryRequest(
				List.of(new Filter(List.of(new Criterion(filterType, value)), 1, pageSize)), FLAGS);
		QueryResponse response;
		try {
			response = send(request);
		}
		catch (ResourceAccessException ex) {
			// The query only reads, but it is a POST. When a kept-alive connection turns
			// out to be closed, the JDK client sends a GET again by itself, never a POST;
			// so a read that failed on the connection gets one more try here.
			try {
				response = send(request);
			}
			catch (RestClientException retryEx) {
				retryEx.addSuppressed(ex);
				throw new StoreException(store(), "query for " + value + " failed", retryEx);
			}
		}
		catch (RestClientException ex) {
			throw new StoreException(store(), "query for " + value + " failed", ex);
		}
		if (response == null || response.results() == null || response.results().isEmpty()) {
			throw new StoreException(store(), "query for " + value + " returned no results block");
		}
		List<Extension> extensions = response.results().getFirst().extensions();
		return (extensions != null) ? extensions : List.of();
	}

	private QueryResponse send(QueryRequest request) {
		return this.http.post()
			.uri("/_apis/public/gallery/extensionquery")
			.header(HttpHeaders.ACCEPT, API_VERSION)
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(QueryResponse.class);
	}

	record QueryRequest(List<Filter> filters, int flags) {
	}

	record Filter(List<Criterion> criteria, int pageNumber, int pageSize) {
	}

	record Criterion(int filterType, String value) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record QueryResponse(List<Result> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Result(List<Extension> extensions) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Extension(Publisher publisher, String extensionName, String displayName, List<Version> versions,
			List<Statistic> statistics) {

		Double statistic(String name) {
			if (this.statistics == null) {
				return null;
			}
			return this.statistics.stream()
				.filter(statistic -> statistic.statisticName().equals(name))
				.map(Statistic::value)
				.findFirst()
				.orElse(null);
		}

		long statisticOrZero(String name) {
			Double value = statistic(name);
			return (value != null) ? Math.round(value) : 0;
		}

		String latestVersion() {
			return (this.versions == null || this.versions.isEmpty()) ? null : this.versions.getFirst().version();
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Publisher(String publisherName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Reviews(List<Review> reviews) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Review(long id, String userDisplayName, Instant updatedDate, int rating, String text, String productVersion,
			boolean isDeleted, boolean isIgnored, Reply reply) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Reply(String replyText, boolean isDeleted) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Version(String version) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Statistic(String statisticName, double value) {
	}

}
