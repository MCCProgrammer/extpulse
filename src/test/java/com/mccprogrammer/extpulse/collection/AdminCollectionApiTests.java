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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminCollectionApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void noListings() {
		// No listings, so the run makes no calls to the real stores.
		this.jdbc.sql("DELETE FROM users").update();
	}

	@Test
	void anAdminCanRunTheCollectionNow() throws Exception {
		String admin = TestAccounts.adminTokenFor(this.mvc, this.jdbc, "admin@example.com");

		this.mvc.perform(post("/api/admin/collect").header("Authorization", "Bearer " + admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.collected").value(0));
	}

	@Test
	void aUserCannot() throws Exception {
		String user = TestAccounts.tokenFor(this.mvc, "miguel@example.com");

		this.mvc.perform(post("/api/admin/collect").header("Authorization", "Bearer " + user))
			.andExpect(status().isForbidden());
	}

}
