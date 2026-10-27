package com.mccprogrammer.extpulse.alerts;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alert settings ({@code extpulse.alerts.*} in application.yaml).
 *
 * @param mailFrom the From of alert emails
 * @param discordWebhookPrefixes a Discord webhook URL must start with one of these
 * @param maxAttempts how many times delivery of one alert is tried
 */
@ConfigurationProperties("extpulse.alerts")
record AlertProperties(String mailFrom, List<String> discordWebhookPrefixes, int maxAttempts) {
}
