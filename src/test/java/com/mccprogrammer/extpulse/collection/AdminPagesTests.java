package com.mccprogrammer.extpulse.collection;

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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminPagesTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void noListings() {
		// No listings, so a run makes no calls to the real stores.
		this.jdbc.sql("DELETE FROM users").update();
	}

	@Test
	void anAdminRunsTheCollectionAndSeesIt() throws Exception {
		MockHttpSession admin = TestAccounts.adminSessionFor(this.mvc, this.jdbc, "admin@example.com");

		this.mvc.perform(post("/app/admin/collect").session(admin).with(csrf()))
			.andExpect(flash().attribute("notice", "Collection succeeded: 0 of 0 listings."));
		this.mvc.perform(get("/app/admin").session(admin))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("SUCCEEDED")));
		this.mvc.perform(get("/app").session(admin)).andExpect(content().string(containsString("/app/admin")));
	}

	@Test
	void aUserHasNoAdminPage() throws Exception {
		MockHttpSession user = TestAccounts.sessionFor(this.mvc, "miguel@example.com");

		this.mvc.perform(get("/app/admin").session(user)).andExpect(status().isForbidden());
		this.mvc.perform(post("/app/admin/collect").session(user).with(csrf())).andExpect(status().isForbidden());
		this.mvc.perform(get("/app").session(user)).andExpect(content().string(not(containsString("/app/admin"))));
	}

}
