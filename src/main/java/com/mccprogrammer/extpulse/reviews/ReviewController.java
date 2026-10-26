package com.mccprogrammer.extpulse.reviews;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.mccprogrammer.extpulse.ApiProblem;
import com.mccprogrammer.extpulse.auth.AuthenticatedAccount;
import com.mccprogrammer.extpulse.reviews.ReviewQueries.Filter;
import com.mccprogrammer.extpulse.reviews.ReviewQueries.ReviewRow;
import com.mccprogrammer.extpulse.store.Store;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * One inbox for the reviews of every store (the Marketplace and Open VSX; the Chrome
 * Web Store's are not read yet).
 */
@RestController
class ReviewController {

	private final ReviewQueries queries;

	private final Clock clock;

	ReviewController(ReviewQueries queries, Clock clock) {
		this.queries = queries;
		this.clock = clock;
	}

	/**
	 * Your reviews, newest first.
	 * @param unread only unread (true) or only read (false) reviews
	 * @param unanswered only Marketplace reviews without your reply (Open VSX has no replies)
	 * @param maxRating only reviews with at most this many stars
	 */
	@GetMapping("/api/reviews")
	ReviewPage inbox(@AuthenticationPrincipal Jwt token, @RequestParam(required = false) Boolean unread,
			@RequestParam(required = false) Boolean unanswered,
			@RequestParam(required = false) @Min(1) @Max(5) Integer maxRating,
			@RequestParam(required = false) Store store, @RequestParam(required = false) Long extensionId,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
			@RequestParam(defaultValue = "0") @Min(0) int offset) {
		List<ReviewRow> rows = this.queries.inbox(AuthenticatedAccount.id(token),
				new Filter(unread, unanswered, maxRating, store, extensionId), limit, offset);
		long total = rows.isEmpty() ? 0 : rows.getFirst().total();
		return new ReviewPage(total, limit, offset, rows.stream().map(ReviewResponse::of).toList());
	}

	@PatchMapping("/api/reviews/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void update(@AuthenticationPrincipal Jwt token, @PathVariable long id, @Valid @RequestBody ReviewUpdate update) {
		if (!this.queries.markRead(AuthenticatedAccount.id(token), id, update.read(), this.clock.instant())) {
			throw ApiProblem.of(HttpStatus.NOT_FOUND, "You have no review " + id);
		}
	}

	record ReviewUpdate(@NotNull Boolean read) {
	}

	record ReviewPage(long total, int limit, int offset, List<ReviewResponse> items) {
	}

	/**
	 * @param replied whether you replied, on stores with replies; {@code null} on Open VSX
	 */
	record ReviewResponse(long id, long extensionId, String extensionName, Store store, int rating, String text,
			String author, String productVersion, Instant postedAt, String reply, Boolean replied, boolean read,
			Instant firstSeenAt) {

		static ReviewResponse of(ReviewRow row) {
			Boolean replied = (row.store() == Store.VS_MARKETPLACE) ? row.reply() != null : null;
			return new ReviewResponse(row.id(), row.extensionId(), row.extensionName(), row.store(), row.rating(),
					row.text(), row.author(), row.productVersion(), row.postedAt().toInstant(), row.reply(), replied,
					row.readAt() != null, row.firstSeenAt().toInstant());
		}

	}

}
