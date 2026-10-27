package com.mccprogrammer.extpulse.alerts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.jayway.jsonpath.JsonPath;
import com.mccprogrammer.extpulse.TestAccounts;
import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import com.mccprogrammer.extpulse.collection.CollectionFinished;
import com.mccprogrammer.extpulse.collection.CollectionRunStatus;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The alerts API and delivery: WireMock stands in for Discord, GreenMail is a real SMTP
 * server inside the test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "extpulse.collection.cron=-", "extpulse.alerts.delivery-cron=-",
		"extpulse.alerts.weekly-summary-cron=-" })
@AutoConfigureMockMvc
class AlertApiAndDeliveryTests {

	private static final String WEBHOOK_PATH = "/api/webhooks/123/secret-token";

	@RegisterExtension
	static WireMockExtension discord = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
		.build();

	// A fixed free port, picked once: one test stops and restarts the server, and a
	// dynamic port would come back different from the one the app was given.
	private static final int SMTP_PORT = freePort();

	@RegisterExtension
	static GreenMailExtension mail = new GreenMailExtension(new ServerSetup(SMTP_PORT, null, ServerSetup.PROTOCOL_SMTP))
		.withPerMethodLifecycle(false);

	@DynamicPropertySource
	static void discordAndMailInTheTest(DynamicPropertyRegistry registry) {
		registry.add("extpulse.alerts.discord-webhook-prefixes", () -> discord.baseUrl() + "/api/webhooks/");
		registry.add("spring.mail.host", () -> "localhost");
		registry.add("spring.mail.port", () -> SMTP_PORT);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private AlertService alerts;

	@Autowired
	private ApplicationEventPublisher events;

	private String token;

	@BeforeEach
	void freshAccountAndInboxes() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		mail.purgeEmailFromAllMailboxes();
		discord.resetAll();
		discord.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(WEBHOOK_PATH)
			.willReturn(aResponse().withStatus(204)));
		this.token = TestAccounts.tokenFor(this.mvc, "miguel@example.com");
	}

	@Test
	void anEmailAlertGoesToTheAccountsEmailUnlessToldOtherwise() throws Exception {
		addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.target").value("miguel@example.com"))
			.andExpect(jsonPath("$.threshold").value(2));
		addRule("{\"type\": \"WEEKLY_SUMMARY\", \"channel\": \"EMAIL\", \"target\": \"team@example.com\"}")
			.andExpect(status().isCreated());

		this.mvc.perform(get("/api/alerts").header("Authorization", "Bearer " + this.token))
			.andExpect(jsonPath("$[*].target")
				.value(org.hamcrest.Matchers.contains("miguel@example.com", "team@example.com")));
	}

	@Test
	void aDiscordAlertNeedsADiscordWebhookAndHidesItsToken() throws Exception {
		addRule("{\"type\": \"NEW_VERSION\", \"channel\": \"DISCORD\", \"target\": \"https://evil.example.com/hook\"}")
			.andExpect(status().isBadRequest());
		addRule("{\"type\": \"NEW_VERSION\", \"channel\": \"DISCORD\"}").andExpect(status().isBadRequest());

		addRule(discordRule("NEW_VERSION")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.target").value(endsWith("/api/webhooks/123/****")));
	}

	@Test
	void rejectsThresholdsThatMakeNoSenseAndDuplicates() throws Exception {
		addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\", \"threshold\": 5}")
			.andExpect(status().isBadRequest());
		addRule("{\"type\": \"NEW_VERSION\", \"channel\": \"EMAIL\", \"threshold\": 3}")
			.andExpect(status().isBadRequest());
		addRule("{\"type\": \"SOMETHING\", \"channel\": \"EMAIL\"}").andExpect(status().isBadRequest());

		addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\"}").andExpect(status().isCreated());
		addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\"}").andExpect(status().isConflict());
	}

	@Test
	void anEmailTargetMustBeAnEmailAndIsCheckedQuickly() throws Exception {
		for (String target : new String[] { "not-an-email", "a@b", "two@@example.com", "spaces in@example.com" }) {
			addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\", \"target\": \"%s\"}".formatted(target))
				.andExpect(status().isBadRequest());
		}
		// Built to make a backtracking pattern slow: many dots and no valid ending.
		String crafted = "a@" + "a.".repeat(120) + "@";
		long start = System.nanoTime();
		addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\", \"target\": \"%s\"}".formatted(crafted))
			.andExpect(status().isBadRequest());
		assertThat(java.time.Duration.ofNanos(System.nanoTime() - start)).isLessThan(java.time.Duration.ofSeconds(1));
	}

	@Test
	void onlyTheOwnerCanDeleteOrTestARule() throws Exception {
		long rule = ruleId(addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\"}"));
		String other = TestAccounts.tokenFor(this.mvc, "someone@example.com");

		this.mvc.perform(delete("/api/alerts/" + rule).header("Authorization", "Bearer " + other))
			.andExpect(status().isNotFound());
		this.mvc.perform(post("/api/alerts/" + rule + "/test").header("Authorization", "Bearer " + other))
			.andExpect(status().isNotFound());
		this.mvc.perform(delete("/api/alerts/" + rule).header("Authorization", "Bearer " + this.token))
			.andExpect(status().isNoContent());
	}

	@Test
	void aTestAlertReachesDiscordWithoutPingingAnyone() throws Exception {
		long rule = ruleId(addRule(discordRule("LOW_RATING")));

		this.mvc.perform(post("/api/alerts/" + rule + "/test").header("Authorization", "Bearer " + this.token))
			.andExpect(status().isNoContent());

		discord.verify(postRequestedFor(urlEqualTo(WEBHOOK_PATH))
			.withRequestBody(matchingJsonPath("$.content",
					com.github.tomakehurst.wiremock.client.WireMock.containing("**ExtPulse test alert**")))
			.withRequestBody(equalToJson("{\"allowed_mentions\": {\"parse\": []}}", true, true)));
	}

	@Test
	void aTestAlertReachesTheMailbox() throws Exception {
		long rule = ruleId(addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\"}"));

		this.mvc.perform(post("/api/alerts/" + rule + "/test").header("Authorization", "Bearer " + this.token))
			.andExpect(status().isNoContent());

		MimeMessage[] received = mail.getReceivedMessages();
		assertThat(received).hasSize(1);
		assertThat(received[0].getSubject()).isEqualTo("ExtPulse test alert");
		assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("miguel@example.com");
		assertThat(GreenMailUtil.getBody(received[0])).contains("LOW_RATING");
	}

	@Test
	void aChannelThatFailsIsReportedOnTheTest() throws Exception {
		long rule = ruleId(addRule(discordRule("LOW_RATING")));
		discord.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(WEBHOOK_PATH)
			.willReturn(aResponse().withStatus(500)));

		this.mvc.perform(post("/api/alerts/" + rule + "/test").header("Authorization", "Bearer " + this.token))
			.andExpect(status().isBadGateway());
	}

	@Test
	void aLowRatingAfterACollectionIsSentOnceToDiscord() throws Exception {
		addRule(discordRule("LOW_RATING"));
		freshOneStarReview("@everyone this is broken");

		// What the collection publishes when a run ends.
		this.events.publishEvent(new CollectionFinished(1, CollectionRunStatus.SUCCEEDED, java.time.Instant.now()));
		this.events.publishEvent(new CollectionFinished(2, CollectionRunStatus.SUCCEEDED, java.time.Instant.now()));

		discord.verify(1,
				postRequestedFor(urlEqualTo(WEBHOOK_PATH))
					.withRequestBody(matchingJsonPath("$.content",
							com.github.tomakehurst.wiremock.client.WireMock
								.containing("1★ review of Diff Checker on VS Code Marketplace")))
					.withRequestBody(equalToJson("{\"allowed_mentions\": {\"parse\": []}}", true, true)));
		assertThat(this.jdbc.sql("SELECT sent_at IS NOT NULL FROM alert_events").query(Boolean.class).single())
			.isTrue();
	}

	@Test
	void aFailedSendIsRetriedAndThenGivenUp() throws Exception {
		addRule(discordRule("LOW_RATING"));
		freshOneStarReview("broken");
		discord.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(WEBHOOK_PATH)
			.willReturn(aResponse().withStatus(503)));

		this.alerts.evaluate();
		for (int i = 0; i < 7; i++) {
			this.alerts.deliverPending();
		}

		// Five attempts (extpulse.alerts.max-attempts), then no more.
		discord.verify(5, postRequestedFor(urlEqualTo(WEBHOOK_PATH)));
		assertThat(this.jdbc.sql("SELECT attempts FROM alert_events").query(Integer.class).single()).isEqualTo(5);
		assertThat(this.jdbc.sql("SELECT last_error FROM alert_events").query(String.class).single()).contains("503");
	}

	@Test
	void aFailedSendGoesOutOnTheNextTry() throws Exception {
		addRule("{\"type\": \"LOW_RATING\", \"channel\": \"EMAIL\"}");
		freshOneStarReview("broken");
		mail.stop();
		this.alerts.evaluate();
		this.alerts.deliverPending();
		assertThat(this.jdbc.sql("SELECT sent_at IS NULL AND attempts = 1 FROM alert_events")
			.query(Boolean.class)
			.single()).isTrue();

		mail.start();
		this.alerts.deliverPending();

		assertThat(mail.getReceivedMessages()).singleElement()
			.satisfies((message) -> assertThat(message.getSubject())
				.isEqualTo("1★ review of Diff Checker on VS Code Marketplace"));
	}

	private void freshOneStarReview(String text) {
		long user = TestAccounts.idOf(this.jdbc, "miguel@example.com");
		long publisher = id(
				"INSERT INTO publishers (user_id, name) VALUES (%d, 'MCCProgrammer') RETURNING id".formatted(user));
		long extension = id(
				"INSERT INTO extensions (publisher_id, display_name) VALUES (%d, 'Diff Checker') RETURNING id"
					.formatted(publisher));
		long listing = id("""
				INSERT INTO listings (extension_id, store, external_id, created_at)
				VALUES (%d, 'VS_MARKETPLACE', 'MCCProgrammer.diff-checker', now() - interval '30 days') RETURNING id"""
			.formatted(extension));
		this.jdbc.sql("""
				INSERT INTO reviews (listing_id, external_id, rating, text, author, posted_at)
				VALUES (:listing, '1', 1, :text, 'Reviewer', now() - interval '1 hour')""")
			.param("listing", listing)
			.param("text", text)
			.update();
	}

	private static String discordRule(String type) {
		return "{\"type\": \"%s\", \"channel\": \"DISCORD\", \"target\": \"%s\"}".formatted(type,
				discord.baseUrl() + WEBHOOK_PATH);
	}

	private ResultActions addRule(String body) throws Exception {
		return this.mvc.perform(post("/api/alerts").header("Authorization", "Bearer " + this.token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long ruleId(ResultActions created) throws Exception {
		return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private long id(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
