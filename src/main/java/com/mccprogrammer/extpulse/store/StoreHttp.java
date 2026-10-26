package com.mccprogrammer.extpulse.store;

import java.net.URI;
import java.net.http.HttpClient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client every store source uses: the same timeouts and User-Agent for all.
 */
final class StoreHttp {

	static final String USER_AGENT = "ExtPulse (+https://github.com/MCCProgrammer/extpulse)";

	private StoreHttp() {
	}

	static RestClient client(URI baseUrl, StoreProperties properties) {
		// Redirects are not followed here: the Chrome Web Store sends Location headers the
		// JDK can't parse, so ChromeWebStoreSource follows them itself.
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(baseUrl.toString())
				.requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
				.build();
	}

}
