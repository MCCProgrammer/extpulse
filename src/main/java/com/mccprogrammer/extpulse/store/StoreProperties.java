package com.mccprogrammer.extpulse.store;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the stores are and how long to wait for them ({@code extpulse.stores.*} in
 * application.yaml). Tests point the URLs at WireMock.
 *
 * @param chromeWebStoreReviewLanguages the languages whose Chrome Web Store reviews are
 * read: the store shows the reviews of one language at a time
 */
@ConfigurationProperties("extpulse.stores")
public record StoreProperties(URI marketplaceUrl, URI openVsxUrl, URI chromeWebStoreUrl, Duration connectTimeout,
		Duration readTimeout, List<String> chromeWebStoreReviewLanguages) {

	public StoreProperties {
		chromeWebStoreReviewLanguages = (chromeWebStoreReviewLanguages == null
				|| chromeWebStoreReviewLanguages.isEmpty()) ? List.of("en")
						: List.copyOf(chromeWebStoreReviewLanguages);
	}

}
