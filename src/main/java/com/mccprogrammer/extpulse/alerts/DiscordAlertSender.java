package com.mccprogrammer.extpulse.alerts;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Posts alerts to a Discord channel through a webhook URL the user copies from the
 * channel's settings (Integrations, Webhooks).
 */
@Component
class DiscordAlertSender implements AlertSender {

	// Discord refuses a message longer than this.
	private static final int MAX_CONTENT = 2000;

	private final RestClient http;

	private final AlertProperties properties;

	DiscordAlertSender(AlertProperties properties) {
		this.properties = properties;
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		this.http = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public AlertChannel channel() {
		return AlertChannel.DISCORD;
	}

	@Override
	public void send(String target, AlertMessage message) {
		// Checked when the rule is saved too; checked again because this is where the
		// app calls a URL a user typed.
		if (!isWebhook(target)) {
			throw new IllegalArgumentException("Not a Discord webhook URL");
		}
		String content = "**" + message.title() + "**\n" + message.body();
		if (content.length() > MAX_CONTENT) {
			content = content.substring(0, MAX_CONTENT - 1) + "…";
		}
		this.http.post()
			.uri(URI.create(target))
			.contentType(MediaType.APPLICATION_JSON)
			// No @everyone or @role pings, whatever a review's text says.
			.body(Map.of("content", content, "allowed_mentions", Map.of("parse", List.of())))
			.retrieve()
			.toBodilessEntity();
	}

	boolean isWebhook(String url) {
		return url != null && this.properties.discordWebhookPrefixes().stream().anyMatch(url::startsWith)
				&& !url.contains("..") && !url.contains("@");
	}

}
