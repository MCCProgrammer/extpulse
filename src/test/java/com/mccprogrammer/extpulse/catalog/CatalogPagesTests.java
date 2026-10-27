package com.mccprogrammer.extpulse.catalog;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mccprogrammer.extpulse.TestAccounts;
import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalog's forms, with the real store adapters pointed at WireMock: each change
 * comes back to the page with what happened.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogPagesTests {

	private static final String DIRECTORS_CUT = "kpjlaobgikkhpngdkladodopdkigmdei";

	@RegisterExtension
	static WireMockExtension stores = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock").http2PlainDisabled(true))
		.build();

	@DynamicPropertySource
	static void storesOnWireMock(DynamicPropertyRegistry registry) {
		registry.add("extpulse.stores.marketplace-url", stores::baseUrl);
		registry.add("extpulse.stores.open-vsx-url", stores::baseUrl);
		registry.add("extpulse.stores.chrome-web-store-url", stores::baseUrl);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private MockHttpSession session;

	@BeforeEach
	void signIn() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		this.session = TestAccounts.sessionFor(this.mvc, "miguel@example.com");
	}

	@Test
	void aPublisherIsFoundOnTheStoresAndAdded() throws Exception {
		stores.stubFor(WireMock.post("/_apis/public/gallery/extensionquery")
			.withRequestBody(matchingJsonPath("$.filters[0].criteria[0].value", equalTo("MCCProgrammer")))
			.willReturn(aResponse().withHeader("Content-Type", "application/json")
				.withBodyFile("marketplace/publisher-MCCProgrammer.json")));
		stores.stubFor(WireMock.get("/api/MCCProgrammer").willReturn(aResponse().withStatus(404)));

		this.mvc
			.perform(post("/app/catalog/publishers").param("name", "MCCProgrammer").session(this.session).with(csrf()))
			.andExpect(redirectedUrl("/app/catalog"))
			.andExpect(flash().attribute("notice", startsWith("Added MCCProgrammer with ")));
		this.mvc.perform(get("/app/catalog").session(this.session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<li>MCCProgrammer</li>")));
	}

	@Test
	void anInvalidFormSaysWhichFieldIsWrong() throws Exception {
		this.mvc
			.perform(post("/app/catalog/publishers").param("name", "not a name!").session(this.session).with(csrf()))
			.andExpect(flash().attribute("error", startsWith("Check the form: [name ")));
	}

	@Test
	void aChromeExtensionIsAddedInTwoSteps() throws Exception {
		long publisher = this.jdbc
			.sql("INSERT INTO publishers (user_id, name) VALUES (:user, 'MCCProgrammer') RETURNING id")
			.param("user", TestAccounts.idOf(this.jdbc, "miguel@example.com"))
			.query(Long.class)
			.single();
		this.mvc
			.perform(post("/app/catalog/extensions").param("publisherId", Long.toString(publisher))
				.param("displayName", "Director's Cut")
				.session(this.session)
				.with(csrf()))
			.andExpect(flash().attribute("notice", "Added Director's Cut. Now add where it is published."));
		long extension = this.jdbc.sql("SELECT id FROM extensions WHERE display_name = 'Director''s Cut'")
			.query(Long.class)
			.single();

		stores.stubFor(WireMock.get(urlPathEqualTo("/detail/" + DIRECTORS_CUT))
			.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8")
				.withBodyFile("chrome-web-store/" + DIRECTORS_CUT + ".html")));
		this.mvc
			.perform(post("/app/catalog/listings").param("extensionId", Long.toString(extension))
				.param("store", "CHROME_WEB_STORE")
				.param("externalId", DIRECTORS_CUT)
				.session(this.session)
				.with(csrf()))
			.andExpect(flash().attribute("notice", "Added " + DIRECTORS_CUT + " on Chrome Web Store."));
	}

	@Test
	void aRefusedChangeShowsWhy() throws Exception {
		this.mvc
			.perform(post("/app/catalog/extensions").param("publisherId", "999999")
				.param("displayName", "Nope")
				.session(this.session)
				.with(csrf()))
			.andExpect(flash().attribute("error", "You have no publisher 999999"));
	}

}
