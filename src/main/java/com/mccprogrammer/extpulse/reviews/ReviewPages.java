package com.mccprogrammer.extpulse.reviews;

import java.time.Clock;
import java.util.List;

import com.mccprogrammer.extpulse.auth.SignedInUser;
import com.mccprogrammer.extpulse.reviews.ReviewQueries.Filter;
import com.mccprogrammer.extpulse.reviews.ReviewQueries.ReviewRow;
import com.mccprogrammer.extpulse.store.Store;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The reviews inbox as a page: filters in the address, so a filtered view can be
 * bookmarked, and a button per review to mark it read or unread.
 */
@Controller
class ReviewPages {

	static final int PAGE_SIZE = 25;

	private final ReviewQueries queries;

	private final Clock clock;

	ReviewPages(ReviewQueries queries, Clock clock) {
		this.queries = queries;
		this.clock = clock;
	}

	@GetMapping("/app/reviews")
	String inbox(@AuthenticationPrincipal SignedInUser user, @RequestParam(required = false) Boolean unread,
			@RequestParam(required = false) Integer maxRating, @RequestParam(required = false) Store store,
			@RequestParam(defaultValue = "0") int page, Model model) {
		Integer stars = (maxRating != null && maxRating >= 1 && maxRating <= 5) ? maxRating : null;
		int current = Math.max(page, 0);
		List<ReviewRow> rows = this.queries.inbox(user.id(), new Filter(unread, null, stars, store, null), PAGE_SIZE,
				current * PAGE_SIZE);
		long total = rows.isEmpty() ? 0 : rows.getFirst().total();
		model.addAttribute("reviews", rows);
		model.addAttribute("total", total);
		model.addAttribute("page", current);
		model.addAttribute("lastPage", (total == 0) ? 0 : (int) ((total - 1) / PAGE_SIZE));
		model.addAttribute("unread", unread);
		model.addAttribute("maxRating", stars);
		model.addAttribute("store", store);
		model.addAttribute("stores", Store.values());
		return "reviews/inbox";
	}

	@PostMapping("/app/reviews/{id}/read")
	String markRead(@AuthenticationPrincipal SignedInUser user, @PathVariable long id, @RequestParam boolean read,
			@RequestParam(required = false) Boolean unread, @RequestParam(required = false) Integer maxRating,
			@RequestParam(required = false) Store store, @RequestParam(defaultValue = "0") int page,
			RedirectAttributes redirect) {
		if (!this.queries.markRead(user.id(), id, read, this.clock.instant())) {
			redirect.addFlashAttribute("error", "You have no review " + id + ".");
		}
		// Back to the same view of the inbox.
		addIfSet(redirect, "unread", unread);
		addIfSet(redirect, "maxRating", maxRating);
		addIfSet(redirect, "store", store);
		addIfSet(redirect, "page", (page > 0) ? page : null);
		return "redirect:/app/reviews";
	}

	private static void addIfSet(RedirectAttributes redirect, String name, Object value) {
		if (value != null) {
			redirect.addAttribute(name, value);
		}
	}

}
