package com.mccprogrammer.extpulse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The database itself enforces the rules that matter, so a bug in the code can't break
 * them.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional // each test is rolled back at the end
class SchemaTests {

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long extensionId;

	private long listingId;

	@BeforeEach
	void createAListing() {
		// Other test classes share the database and commit their data; start from empty.
		jdbc.sql("DELETE FROM users").update();
		userId = insertUser("miguel@example.com");
		long publisherId = jdbc
			.sql("INSERT INTO publishers (user_id, name) VALUES (:user, 'MCCProgrammer') RETURNING id")
			.param("user", userId)
			.query(Long.class)
			.single();
		extensionId = jdbc
			.sql("INSERT INTO extensions (publisher_id, display_name) VALUES (:publisher, 'Diff Checker') RETURNING id")
			.param("publisher", publisherId)
			.query(Long.class)
			.single();
		listingId = insertListing("VS_MARKETPLACE", "MCCProgrammer.diff-checker");
	}

	@Test
	void rejectsASecondSnapshotOfTheSameListingOnTheSameDay() {
		insertSnapshot("2026-10-21", 1700);

		assertThatThrownBy(() -> insertSnapshot("2026-10-21", 1729))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void acceptsSnapshotsOfTheSameListingOnDifferentDays() {
		insertSnapshot("2026-10-21", 1700);
		insertSnapshot("2026-10-22", 1729);

		assertThat(count("snapshots")).isEqualTo(2);
	}

	@Test
	void rejectsNegativeInstalls() {
		assertThatThrownBy(() -> insertSnapshot("2026-10-21", -1)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsAnAverageRatingAboveFive() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO snapshots (listing_id, taken_on, installs, rating_avg)
				VALUES (:listing, DATE '2026-10-21', 1700, :rating)""")
			.param("listing", listingId)
			.param("rating", new BigDecimal("5.01"))
			.update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsAnUnknownStore() {
		assertThatThrownBy(() -> insertListing("APP_STORE", "MCCProgrammer.diff-checker"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsTheSameListingTwiceInOneStore() {
		assertThatThrownBy(() -> insertListing("VS_MARKETPLACE", "MCCProgrammer.diff-checker"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsTheSameEmailWithDifferentCase() {
		assertThatThrownBy(() -> insertUser("MIGUEL@example.com")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingAUserDeletesEverythingBelowIt() {
		insertSnapshot("2026-10-21", 1700);

		jdbc.sql("DELETE FROM users WHERE id = :user").param("user", userId).update();

		assertThat(count("publishers")).isZero();
		assertThat(count("extensions")).isZero();
		assertThat(count("listings")).isZero();
		assertThat(count("snapshots")).isZero();
	}

	private long insertUser(String email) {
		return jdbc.sql("INSERT INTO users (email, password_hash) VALUES (:email, 'x') RETURNING id")
			.param("email", email)
			.query(Long.class)
			.single();
	}

	private long insertListing(String store, String externalId) {
		return jdbc.sql(
				"INSERT INTO listings (extension_id, store, external_id) VALUES (:extension, :store, :externalId) RETURNING id")
			.param("extension", extensionId)
			.param("store", store)
			.param("externalId", externalId)
			.query(Long.class)
			.single();
	}

	private void insertSnapshot(String day, long installs) {
		jdbc.sql(
				"INSERT INTO snapshots (listing_id, taken_on, installs) VALUES (:listing, CAST(:day AS date), :installs)")
			.param("listing", listingId)
			.param("day", day)
			.param("installs", installs)
			.update();
	}

	private long count(String table) {
		// Table names can't be bound as parameters; these come only from the tests above.
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

}
