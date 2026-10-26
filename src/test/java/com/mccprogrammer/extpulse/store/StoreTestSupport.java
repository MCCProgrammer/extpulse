package com.mccprogrammer.extpulse.store;

import java.net.URI;
import java.time.Duration;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * WireMock answers in place of the stores, with responses recorded from the real ones
 * (src/test/resources/wiremock/__files).
 */
final class StoreTestSupport {

	static final Duration READ_TIMEOUT = Duration.ofMillis(500);

	private StoreTestSupport() {
	}

	static WireMockExtension wireMock() {
		return WireMockExtension.newInstance()
				.options(wireMockConfig().dynamicPort()
					.usingFilesUnderClasspath("wiremock")
					// Over plain http the JDK client tries an HTTP/2 upgrade (h2c), which
					// WireMock's Jetty resets on requests with a body. The real stores are
					// https, where HTTP/2 is negotiated without an upgrade.
					.http2PlainDisabled(true))
				.build();
	}

	/** Every store pointed at the given WireMock server, with short timeouts. */
	static StoreProperties propertiesFor(WireMockExtension wireMock) {
		URI url = URI.create(wireMock.baseUrl());
		return new StoreProperties(url, url, url, Duration.ofSeconds(1), READ_TIMEOUT);
	}

}
