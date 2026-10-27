package com.mccprogrammer.extpulse.reviews;

import java.time.OffsetDateTime;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReviewPagesTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	private MockHttpSession session;

	private long lowReview;

	@BeforeEach
	void twoReviews() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		this.session = TestAccounts.sessionFor(this.mvc, "miguel@example.com");
		long listing = this.jdbc
			.sql("""
					WITH p AS (INSERT INTO publishers (user_id, name) VALUES (:user, 'MCCProgrammer') RETURNING id),
					     e AS (INSERT INTO extensions (publisher_id, display_name) SELECT id, 'Diff Checker' FROM p RETURNING id)
					INSERT INTO listings (extension_id, store, external_id)
					SELECT id, 'VS_MARKETPLACE', 'MCCProgrammer.diff-checker' FROM e RETURNING id""")
			.param("user", TestAccounts.idOf(this.jdbc, "miguel@example.com"))
			.query(Long.class)
			.single();
		review(listing, "a", 5, "Great tool");
		this.lowReview = review(listing, "b", 1, "Crashes on big files");
	}

	@Test
	void theInboxListsAndFiltersReviews() throws Exception {
		this.mvc.perform(get("/app/reviews").session(this.session))
			.andExpect(model().attribute("total", 2L))
			.andExpect(content().string(allOf(containsString("Great tool"), containsString("Crashes on big files"))));
		this.mvc.perform(get("/app/reviews").param("maxRating", "2").session(this.session))
			.andExpect(model().attribute("total", 1L))
			.andExpect(content().string(not(containsString("Great tool"))));
		this.mvc.perform(get("/app/reviews").param("maxRating", "9").session(this.session))
			.andExpect(model().attribute("total", 2L));
	}

	@Test
	void markingAReviewReadKeepsTheSameView() throws Exception {
		this.mvc
			.perform(post("/app/reviews/{id}/read", this.lowReview).param("read", "true")
				.param("maxRating", "2")
				.param("store", "VS_MARKETPLACE")
				.param("unread", "true")
				.session(this.session)
				.with(csrf()))
			.andExpect(redirectedUrl("/app/reviews?unread=true&maxRating=2&store=VS_MARKETPLACE"));
		assertThat(this.jdbc.sql("SELECT read_at IS NOT NULL FROM reviews WHERE id = :id")
			.param("id", this.lowReview)
			.query(Boolean.class)
			.single()).isTrue();

		this.mvc.perform(get("/app/reviews").param("unread", "true").session(this.session))
			.andExpect(model().attribute("total", 1L));
	}

	@Test
	void anotherAccountsReviewCannotBeMarked() throws Exception {
		MockHttpSession other = TestAccounts.sessionFor(this.mvc, "other@example.com");

		this.mvc
			.perform(post("/app/reviews/{id}/read", this.lowReview).param("read", "true").session(other).with(csrf()))
			.andExpect(flash().attribute("error", "You have no review " + this.lowReview + "."));
	}

	private long review(long listing, String externalId, int rating, String text) {
		return this.jdbc.sql("""
				INSERT INTO reviews (listing_id, external_id, rating, text, author, posted_at)
				VALUES (:listing, :id, :rating, :text, 'someone', :posted) RETURNING id""")
			.param("listing", listing)
			.param("id", externalId)
			.param("rating", rating)
			.param("text", text)
			.param("posted", OffsetDateTime.now().minusDays(rating))
			.query(Long.class)
			.single();
	}

}
