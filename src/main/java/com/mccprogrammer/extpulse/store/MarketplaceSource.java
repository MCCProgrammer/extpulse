package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The VS Code Marketplace, through its {@code extensionquery} API. The API is public
 * but not officially documented, so the version it answers with is pinned.
 * External IDs look like {@code MCCProgrammer.diff-checker}.
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
		return query(BY_PUBLISHER_NAME, publisher, PUBLISHER_PAGE_SIZE).stream()
				.map(extension -> {
					String externalId = extension.publisher().publisherName() + "." + extension.extensionName();
					return new DiscoveredListing(store(), externalId, extension.displayName(),
							this.baseUrl + "/items?itemName=" + externalId);
				})
				.toList();
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

	private List<Extension> query(int filterType, String value, int pageSize) {
		QueryRequest request = new QueryRequest(
				List.of(new Filter(List.of(new Criterion(filterType, value)), 1, pageSize)), FLAGS);
		QueryResponse response;
		try {
			response = this.http.post()
					.uri("/_apis/public/gallery/extensionquery")
					.header(HttpHeaders.ACCEPT, API_VERSION)
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.body(QueryResponse.class);
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
	record Version(String version) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Statistic(String statisticName, double value) {
	}

}
