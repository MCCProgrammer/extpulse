package com.mccprogrammer.extpulse.reviews;

import java.time.Instant;
import java.util.List;

import com.mccprogrammer.extpulse.TestAccounts;
import com.mccprogrammer.extpulse.TestcontainersConfiguration;
import com.mccprogrammer.extpulse.store.StoreReview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReviewsApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private ReviewWriter writer;

	private String token;

	private long markify;

	@BeforeEach
	void createReviews() throws Exception {
		this.jdbc.sql("DELETE FROM users").update();
		this.token = TestAccounts.tokenFor(this.mvc, "miguel@example.com");
		long publisher = id("INSERT INTO publishers (user_id, name) VALUES (%d, 'MCCProgrammer') RETURNING id"
			.formatted(TestAccounts.idOf(this.jdbc, "miguel@example.com")));
		long diffChecker = id(
				"INSERT INTO extensions (publisher_id, display_name) VALUES (%d, 'Diff Checker') RETURNING id"
					.formatted(publisher));
		this.markify = id("INSERT INTO extensions (publisher_id, display_name) VALUES (%d, 'Markify') RETURNING id"
			.formatted(publisher));
		long diffMarketplace = listing(diffChecker, "VS_MARKETPLACE", "MCCProgrammer.diff-checker");
		long markifyOpenVsx = listing(this.markify, "OPEN_VSX", "MCCProgrammer/markify");
		long markifyMarketplace = listing(this.markify, "VS_MARKETPLACE", "MCCProgrammer.markify");

		this.writer.save(diffMarketplace,
				List.of(review("1", 1, "hard to see", "2026-01-29T15:11:13Z", "Thanks, fixed in 0.2.0"),
						review("2", 5, "great", "2026-03-01T10:00:00Z", null)));
		this.writer.save(markifyOpenVsx, List.of(review("github:someone", 2, null, "2026-05-01T10:00:00Z", null)));
		this.writer.save(markifyMarketplace, List.of(review("3", 4, "nice", "2026-04-01T10:00:00Z", null)));
	}

	@Test
	void listsEveryStoresReviewsNewestFirst() throws Exception {
		inbox("").andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(4))
			.andExpect(jsonPath("$.items[*].text")
				.value(contains(nullValue(), equalTo("nice"), equalTo("great"), equalTo("hard to see"))))
			.andExpect(jsonPath("$.items[0].store").value("OPEN_VSX"))
			.andExpect(jsonPath("$.items[0].extensionName").value("Markify"))
			// Open VSX has no replies; on the Marketplace, whether you replied.
			.andExpect(jsonPath("$.items[0].replied").value(nullValue()))
			.andExpect(jsonPath("$.items[3].replied").value(true))
			.andExpect(jsonPath("$.items[3].reply").value("Thanks, fixed in 0.2.0"))
			.andExpect(jsonPath("$.items[*].read").value(contains(false, false, false, false)));
	}

	@Test
	void filtersByStarsStoreExtensionAndReply() throws Exception {
		inbox("?maxRating=2").andExpect(jsonPath("$.items[*].rating").value(contains(2, 1)));
		inbox("?store=VS_MARKETPLACE").andExpect(jsonPath("$.total").value(3));
		inbox("?extensionId=" + this.markify)
			.andExpect(jsonPath("$.items[*].extensionName").value(contains("Markify", "Markify")));
		// Marketplace reviews you haven't replied to; Open VSX ones never count as
		// unanswered.
		inbox("?unanswered=true").andExpect(jsonPath("$.items[*].text").value(contains("nice", "great")));
	}

	@Test
	void chromeWebStoreReviewsHaveRepliesToo() throws Exception {
		long chrome = listing(this.markify, "CHROME_WEB_STORE", "kpjlaobgikkhpngdkladodopdkigmdei");
		this.writer
			.save(chrome, List.of(review("fa09b0ff-8d4a-4f16-a49f-44ae1928850f", 3, "ok", "2026-06-01T10:00:00Z", null),
					review("c973bfb1-c5fe-43bd-a50b-e6acf49f3a8c", 5, "love it", "2026-05-15T10:00:00Z", "Thanks!")));

		inbox("?store=CHROME_WEB_STORE").andExpect(jsonPath("$.items[*].replied").value(contains(false, true)));
		inbox("?unanswered=true").andExpect(jsonPath("$.items[*].text").value(contains("ok", "nice", "great")));
	}

	@Test
	void marksReviewsAsReadAndFiltersThem() throws Exception {
		long newest = this.jdbc.sql("SELECT id FROM reviews ORDER BY posted_at DESC LIMIT 1")
			.query(Long.class)
			.single();

		markRead(this.token, newest, "true").andExpect(status().isNoContent());

		inbox("?unread=true").andExpect(jsonPath("$.total").value(3));
		inbox("?unread=false").andExpect(jsonPath("$.items[0].read").value(true));

		markRead(this.token, newest, "false").andExpect(status().isNoContent());
		inbox("?unread=true").andExpect(jsonPath("$.total").value(4));
	}

	@Test
	void pagesWithLimitAndOffset() throws Exception {
		inbox("?limit=2&offset=2").andExpect(jsonPath("$.total").value(4))
			.andExpect(jsonPath("$.items[*].text").value(contains("great", "hard to see")));
	}

	@Test
	void rejectsFiltersThatMakeNoSense() throws Exception {
		inbox("?maxRating=9").andExpect(status().isBadRequest());
		inbox("?limit=0").andExpect(status().isBadRequest());
		inbox("?store=APP_STORE").andExpect(status().isBadRequest());
		long any = this.jdbc.sql("SELECT min(id) FROM reviews").query(Long.class).single();
		this.mvc
			.perform(patch("/api/reviews/" + any).header("Authorization", "Bearer " + this.token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void anotherAccountSeesNoneOfThem() throws Exception {
		String other = TestAccounts.tokenFor(this.mvc, "someone@example.com");
		long any = this.jdbc.sql("SELECT min(id) FROM reviews").query(Long.class).single();

		this.mvc.perform(get("/api/reviews").header("Authorization", "Bearer " + other))
			.andExpect(jsonPath("$.total").value(0));
		markRead(other, any, "true").andExpect(status().isNotFound());
	}

	private ResultActions inbox(String query) throws Exception {
		return this.mvc.perform(get("/api/reviews" + query).header("Authorization", "Bearer " + this.token));
	}

	private ResultActions markRead(String token, long review, String read) throws Exception {
		return this.mvc.perform(patch("/api/reviews/" + review).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"read\": %s}".formatted(read)));
	}

	private static StoreReview review(String externalId, int rating, String text, String postedAt, String reply) {
		return new StoreReview(externalId, rating, text, "Reviewer", "1.0.0", Instant.parse(postedAt), reply);
	}

	private long listing(long extension, String store, String externalId) {
		return id("INSERT INTO listings (extension_id, store, external_id) VALUES (%d, '%s', '%s') RETURNING id"
			.formatted(extension, store, externalId));
	}

	private long id(String sql) {
		return this.jdbc.sql(sql).query(Long.class).single();
	}

}
