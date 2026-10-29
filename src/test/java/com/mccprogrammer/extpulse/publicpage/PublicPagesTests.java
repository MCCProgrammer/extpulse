package com.mccprogrammer.extpulse.publicpage;

import java.time.LocalDate;
import java.time.ZoneId;

import com.mccprogrammer.extpulse.TestAccounts;
import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public pages and badges exist only for accounts that chose an address, and show
 * installs, nothing private.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PublicPagesTests {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Lisbon"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private MockHttpSession session;

	private long extension;

	@BeforeEach
	void anAccountWithTwoListings() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		this.session = TestAccounts.sessionFor(this.mvc, "miguel@example.com");
		long publisher = this.jdbc
			.sql("INSERT INTO publishers (user_id, name) VALUES (:user, 'MCCProgrammer') RETURNING id")
			.param("user", TestAccounts.idOf(this.jdbc, "miguel@example.com"))
			.query(Long.class)
			.single();
		this.extension = this.jdbc
			.sql("INSERT INTO extensions (publisher_id, display_name) VALUES (:publisher, 'Diff Checker') RETURNING id")
			.param("publisher", publisher)
			.query(Long.class)
			.single();
		snapshot(listing("VS_MARKETPLACE", "MCCProgrammer.diff-checker"), 12_000);
		snapshot(listing("OPEN_VSX", "MCCProgrammer/diff-checker"), 345);
	}

	@Test
	void theHomePageIsPublic() throws Exception {
		this.mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Sign in")));
	}

	@Test
	void anAccountIsPrivateUntilItChoosesAnAddress() throws Exception {
		this.mvc.perform(get("/p/mccprogrammer")).andExpect(status().isNotFound());
		this.mvc.perform(get("/p/mccprogrammer/badge.svg")).andExpect(status().isNotFound());
	}

	@Test
	void thePublicPageShowsInstallsButNotTheEmail() throws Exception {
		choose("mccprogrammer");

		this.mvc.perform(get("/p/MCCProgrammer"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Diff Checker"), containsString("12,345"),
					not(containsString("miguel@example.com")))));
	}

	@Test
	void badgesAreSvgThatMayBeCachedForAnHour() throws Exception {
		choose("mccprogrammer");

		this.mvc.perform(get("/p/mccprogrammer/badge.svg"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/svg+xml"))
			.andExpect(header().string("Cache-Control", "max-age=3600, public"))
			.andExpect(content().string(containsString("installs: 12.3k")));
		this.mvc.perform(get("/p/mccprogrammer/extensions/{id}/badge.svg", this.extension))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(">12.3k<")));
	}

	@Test
	void badgesAlsoComeAsJsonForShieldsIo() throws Exception {
		choose("mccprogrammer");

		this.mvc.perform(get("/p/mccprogrammer/badge.json"))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/json"))
			.andExpect(header().string("Cache-Control", "max-age=3600, public"))
			.andExpect(jsonPath("$.schemaVersion").value(1))
			.andExpect(jsonPath("$.label").value("installs"))
			.andExpect(jsonPath("$.message").value("12.3k"))
			.andExpect(jsonPath("$.color").value("256abf"))
			.andExpect(jsonPath("$.cacheSeconds").value(3600));
		this.mvc.perform(get("/p/mccprogrammer/extensions/{id}/badge.json", this.extension))
			.andExpect(jsonPath("$.message").value("12.3k"));
		this.mvc.perform(get("/p/mccprogrammer/extensions/{id}/badge.json", this.extension + 1000))
			.andExpect(status().isNotFound());
		this.mvc.perform(get("/p/nobody/badge.json")).andExpect(status().isNotFound());
	}

	@Test
	void exactInstallsComeAsJsonThatAnyWebsiteMayRead() throws Exception {
		this.mvc.perform(get("/p/mccprogrammer/installs.json")).andExpect(status().isNotFound());
		choose("mccprogrammer");

		this.mvc.perform(get("/p/mccprogrammer/installs.json"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "*"))
			.andExpect(header().string("Cache-Control", "max-age=3600, public"))
			.andExpect(jsonPath("$.installs").value(12_345))
			.andExpect(jsonPath("$.extensions[0].id").value(this.extension))
			.andExpect(jsonPath("$.extensions[0].name").value("Diff Checker"))
			.andExpect(jsonPath("$.extensions[0].installs").value(12_345))
			.andExpect(content().string(not(containsString("miguel@example.com"))));
	}

	@Test
	void anotherAccountsExtensionHasNoBadgeOnThisPage() throws Exception {
		choose("mccprogrammer");
		this.mvc.perform(get("/p/mccprogrammer/extensions/{id}/badge.svg", this.extension + 1000))
			.andExpect(status().isNotFound());
	}

	@Test
	void anAddressIsCheckedAndCanBeTakenBackOrTurnedOff() throws Exception {
		this.mvc
			.perform(post("/app/settings/public-page").param("slug", "-Not Valid").session(this.session).with(csrf()))
			.andExpect(flash().attribute("error",
					"Use 1 to 40 lowercase letters, digits and dashes, not starting or ending with a dash"));

		choose("mccprogrammer");
		MockHttpSession other = TestAccounts.sessionFor(this.mvc, "other@example.com");
		this.mvc.perform(post("/app/settings/public-page").param("slug", "mccprogrammer").session(other).with(csrf()))
			.andExpect(flash().attribute("error", "Another account already uses mccprogrammer"));

		this.mvc.perform(get("/app/settings").session(this.session))
			.andExpect(content().string(containsString("/p/mccprogrammer/badge.svg")));

		this.mvc.perform(post("/app/settings/public-page").param("slug", "").session(this.session).with(csrf()))
			.andExpect(flash().attribute("notice", "Your public page is off."));
		this.mvc.perform(get("/p/mccprogrammer")).andExpect(status().isNotFound());
	}

	private void choose(String slug) throws Exception {
		this.mvc.perform(post("/app/settings/public-page").param("slug", slug).session(this.session).with(csrf()))
			.andExpect(flash().attribute("notice", "Your public page is on."));
	}

	private long listing(String store, String externalId) {
		return this.jdbc.sql(
				"INSERT INTO listings (extension_id, store, external_id) VALUES (:extension, :store, :id) RETURNING id")
			.param("extension", this.extension)
			.param("store", store)
			.param("id", externalId)
			.query(Long.class)
			.single();
	}

	private void snapshot(long listing, long installs) {
		this.jdbc.sql("INSERT INTO snapshots (listing_id, taken_on, installs) VALUES (:listing, :day, :installs)")
			.param("listing", listing)
			.param("day", TODAY)
			.param("installs", installs)
			.update();
	}

}
