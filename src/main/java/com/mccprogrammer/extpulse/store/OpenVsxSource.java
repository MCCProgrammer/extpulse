package com.mccprogrammer.extpulse.store;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Open VSX, through its documented REST API. External IDs look like
 * {@code MCCProgrammer/diff-checker} (namespace, then extension name).
 */
@Component
class OpenVsxSource implements StoreSource {

	private final RestClient http;

	private final String baseUrl;

	OpenVsxSource(StoreProperties properties) {
		this.http = StoreHttp.client(properties.openVsxUrl(), properties);
		this.baseUrl = properties.openVsxUrl().toString();
	}

	@Override
	public Store store() {
		return Store.OPEN_VSX;
	}

	@Override
	public List<DiscoveredListing> discover(String publisher) {
		Namespace namespace;
		try {
			namespace = this.http.get().uri("/api/{namespace}", publisher).retrieve().body(Namespace.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			return List.of();
		}
		catch (RestClientException ex) {
			throw new StoreException(store(), "namespace " + publisher + " failed", ex);
		}
		if (namespace == null || namespace.extensions() == null) {
			return List.of();
		}
		// The namespace lists names only; the display name needs one call per extension.
		return namespace.extensions()
				.keySet()
				.stream()
				.map(name -> extension(publisher, name))
				.map(extension -> new DiscoveredListing(store(), extension.namespace() + "/" + extension.name(),
						DiscoveredListing.vsCodeExtensionKey(extension.namespace(), extension.name()),
						extension.displayName(),
						this.baseUrl + "/extension/" + extension.namespace() + "/" + extension.name()))
				.toList();
	}

	@Override
	public ListingStats fetchStats(String externalId) {
		String[] parts = externalId.split("/", -1);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new IllegalArgumentException("Not an Open VSX ID (namespace/name): " + externalId);
		}
		Extension extension = extension(parts[0], parts[1]);
		BigDecimal ratingAvg = (extension.averageRating() != null) ? BigDecimal.valueOf(extension.averageRating())
				: null;
		return new ListingStats(orZero(extension.downloadCount()), ratingAvg,
				(int) orZero(extension.reviewCount()), extension.version());
	}

	private Extension extension(String namespace, String name) {
		Extension extension;
		try {
			extension = this.http.get()
					.uri("/api/{namespace}/{name}", namespace, name)
					.retrieve()
					.body(Extension.class);
		}
		catch (HttpClientErrorException.NotFound ex) {
			throw new ListingNotFoundException(store(), namespace + "/" + name);
		}
		catch (RestClientException ex) {
			throw new StoreException(store(), "extension " + namespace + "/" + name + " failed", ex);
		}
		if (extension == null) {
			throw new StoreException(store(), "extension " + namespace + "/" + name + " returned an empty body");
		}
		return extension;
	}

	private static long orZero(Number value) {
		return (value != null) ? value.longValue() : 0;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Namespace(Map<String, String> extensions) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Extension(String namespace, String name, String displayName, String version, Long downloadCount,
			Double averageRating, Integer reviewCount) {
	}

}
