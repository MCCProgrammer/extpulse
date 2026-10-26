package com.mccprogrammer.extpulse.store;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the stores are and how long to wait for them ({@code extpulse.stores.*} in
 * application.yaml). Tests point the URLs at WireMock.
 */
@ConfigurationProperties("extpulse.stores")
public record StoreProperties(URI marketplaceUrl, URI openVsxUrl, URI chromeWebStoreUrl, Duration connectTimeout,
		Duration readTimeout) {
}
