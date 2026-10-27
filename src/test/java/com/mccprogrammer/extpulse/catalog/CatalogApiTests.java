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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalog API with the real store adapters, pointed at WireMock serving the responses
 * recorded from the stores.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiTests {

	private static final String[] OPEN_VSX_EXTENSIONS = { "debug-dataStructures-visualizer-extension",
			"debug-sorting-algorithms-visualizer", "diff-checker", "github-style-markdown-preview", "markify" };

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

	private String token;

	@BeforeEach
	void createAccountAndStubTheStores() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		this.token = TestAccounts.tokenFor(this.mvc, "miguel@example.com");
		stubMarketplacePublisher("MCCProgrammer", "marketplace/publisher-MCCProgrammer.json");
		stubJson("/api/MCCProgrammer", 200, "open-vsx/namespace-MCCProgrammer.json");
		for (String name : OPEN_VSX_EXTENSIONS) {
			stubJson("/api/MCCProgrammer/" + name, 200, "open-vsx/MCCProgrammer-" + name + ".json");
		}
	}

	@Test
	void addsAPublisherWithOneExtensionPerExtensionAcrossStores() throws Exception {
		addPublisher(this.token, "MCCProgrammer").andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("MCCProgrammer"))
			.andExpect(jsonPath("$.extensions", hasSize(5)))
			.andExpect(jsonPath("$.extensions[?(@.displayName == 'Diff Checker')].listings[*].externalId",
					containsInAnyOrder("MCCProgrammer.diff-checker", "MCCProgrammer/diff-checker")));

		assertThat(count("extensions")).isEqualTo(5);
		assertThat(count("listings")).isEqualTo(10);
	}

	@Test
	void listsThePublishersOfTheAccount() throws Exception {
		addPublisher(this.token, "MCCProgrammer");

		this.mvc.perform(get("/api/publishers").header("Authorization", "Bearer " + this.token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", containsInAnyOrder("MCCProgrammer")));
	}

	@Test
	void addingTheSamePublisherTwiceIsAConflict() throws Exception {
		addPublisher(this.token, "MCCProgrammer");

		addPublisher(this.token, "MCCProgrammer").andExpect(status().isConflict());
	}

	@Test
	void listingsAnotherAccountTracksAreAConflict() throws Exception {
		addPublisher(this.token, "MCCProgrammer").andExpect(status().isCreated());
		String other = TestAccounts.tokenFor(this.mvc, "someone@example.com");

		addPublisher(other, "MCCProgrammer").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("already tracked")));
	}

	@Test
	void aPublisherNoStoreKnowsIsNotFound() throws Exception {
		stubMarketplacePublisher("Nobody", "marketplace/not-found.json");
		stubJson("/api/Nobody", 404, "open-vsx/namespace-not-found.json");

		addPublisher(this.token, "Nobody").andExpect(status().isNotFound());
	}

	@Test
	void whenAStoreFailsNothingIsAdded() throws Exception {
		stores.stubFor(WireMock.post("/_apis/public/gallery/extensionquery").willReturn(aResponse().withStatus(500)));

		addPublisher(this.token, "MCCProgrammer").andExpect(status().isBadGateway());

		assertThat(count("publishers")).isZero();
	}

	@Test
	void rejectsAPublisherNameThatIsNotAName() throws Exception {
		addPublisher(this.token, "../admin").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void addsAChromeExtensionByItsItemId() throws Exception {
		long extension = addExtensionNamed("Director's Cut for Letterboxd");
		stubChromePage(DIRECTORS_CUT, "chrome-web-store/" + DIRECTORS_CUT + ".html");

		addListing(this.token, extension, "CHROME_WEB_STORE", DIRECTORS_CUT).andExpect(status().isCreated())
			.andExpect(jsonPath("$.store").value("CHROME_WEB_STORE"))
			.andExpect(jsonPath("$.externalId").value(DIRECTORS_CUT));
	}

	@Test
	void aListingTheStoreDoesNotHaveIsRejected() throws Exception {
		long extension = addExtensionNamed("Ghost");
		String unknown = "a".repeat(32);
		stubChromePage(unknown, "chrome-web-store/not-found.html");

		addListing(this.token, extension, "CHROME_WEB_STORE", unknown).andExpect(status().isUnprocessableContent());
		addListing(this.token, extension, "CHROME_WEB_STORE", "not-an-id").andExpect(status().isBadRequest());
	}

	@Test
	void anotherAccountsExtensionLooksMissing() throws Exception {
		long extension = addExtensionNamed("Director's Cut for Letterboxd");
		String other = TestAccounts.tokenFor(this.mvc, "someone@example.com");

		addListing(other, extension, "CHROME_WEB_STORE", DIRECTORS_CUT).andExpect(status().isNotFound());
	}

	@Test
	void needsALogin() throws Exception {
		this.mvc.perform(post("/api/publishers").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"x\"}"))
			.andExpect(status().isUnauthorized());
	}

	private long addExtensionNamed(String displayName) throws Exception {
		addPublisher(this.token, "MCCProgrammer");
		long publisher = this.jdbc.sql("SELECT id FROM publishers").query(Long.class).single();
		String body = this.mvc
			.perform(post("/api/extensions").header("Authorization", "Bearer " + this.token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"publisherId": %d, "displayName": "%s"}""".formatted(publisher, displayName)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions addPublisher(String token, String name) throws Exception {
		return this.mvc.perform(post("/api/publishers").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"%s\"}".formatted(name)));
	}

	private ResultActions addListing(String token, long extension, String store, String externalId) throws Exception {
		return this.mvc
			.perform(post("/api/extensions/" + extension + "/listings").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"store\": \"%s\", \"externalId\": \"%s\"}".formatted(store, externalId)));
	}

	private void stubMarketplacePublisher(String publisher, String bodyFile) {
		stores.stubFor(WireMock.post("/_apis/public/gallery/extensionquery")
			.withRequestBody(matchingJsonPath("$.filters[0].criteria[0].filterType", equalTo("18")))
			.withRequestBody(matchingJsonPath("$.filters[0].criteria[0].value", equalTo(publisher)))
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBodyFile(bodyFile)));
	}

	private void stubJson(String path, int status, String bodyFile) {
		stores.stubFor(WireMock.get(path)
			.willReturn(aResponse().withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBodyFile(bodyFile)));
	}

	private void stubChromePage(String itemId, String bodyFile) {
		stores.stubFor(WireMock.get(urlPathEqualTo("/detail/" + itemId))
			.willReturn(aResponse().withHeader("Content-Type", "text/html; charset=utf-8").withBodyFile(bodyFile)));
	}

	private long count(String table) {
		return this.jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

}
