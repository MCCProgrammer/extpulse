package com.mccprogrammer.extpulse.catalog;

import com.mccprogrammer.extpulse.auth.SignedInUser;
import com.mccprogrammer.extpulse.store.Store;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The dashboard's page for choosing what to track: the same operations as the catalog
 * API, as forms.
 */
@Controller
class CatalogPages {

	private final CatalogService catalog;

	CatalogPages(CatalogService catalog) {
		this.catalog = catalog;
	}

	@GetMapping("/app/catalog")
	String catalog(@AuthenticationPrincipal SignedInUser user, Model model) {
		model.addAttribute("publishers", this.catalog.publishersOf(user.id()));
		model.addAttribute("extensions", this.catalog.extensionsOf(user.id()));
		model.addAttribute("stores", Store.values());
		return "catalog/catalog";
	}

	@PostMapping("/app/catalog/publishers")
	String addPublisher(@AuthenticationPrincipal SignedInUser user, @Valid @ModelAttribute PublisherForm form,
			BindingResult result, RedirectAttributes redirect) {
		return saved(redirect, result, () -> {
			int found = this.catalog.addPublisher(user.id(), form.name()).extensions().size();
			return "Added " + form.name() + " with " + found + ((found == 1) ? " extension." : " extensions.");
		});
	}

	@PostMapping("/app/catalog/extensions")
	String addExtension(@AuthenticationPrincipal SignedInUser user, @Valid @ModelAttribute ExtensionForm form,
			BindingResult result, RedirectAttributes redirect) {
		return saved(redirect, result, () -> {
			this.catalog.addExtension(user.id(), form.publisherId(), form.displayName());
			return "Added " + form.displayName() + ". Now add where it is published.";
		});
	}

	@PostMapping("/app/catalog/listings")
	String addListing(@AuthenticationPrincipal SignedInUser user, @Valid @ModelAttribute ListingForm form,
			BindingResult result, RedirectAttributes redirect) {
		return saved(redirect, result, () -> {
			Listing listing = this.catalog.addListing(user.id(), form.extensionId(), form.store(), form.externalId());
			return "Added " + listing.getExternalId() + " on " + listing.getStore().displayName() + ".";
		});
	}

	@PostMapping("/app/catalog/extensions/{id}/remove")
	String removeExtension(@AuthenticationPrincipal SignedInUser user, @PathVariable long id,
			RedirectAttributes redirect) {
		try {
			redirect.addFlashAttribute("notice",
					"Removed " + this.catalog.removeExtension(user.id(), id) + " and its history.");
		}
		catch (ErrorResponseException ex) {
			redirect.addFlashAttribute("error", ex.getBody().getDetail());
		}
		return "redirect:/app/catalog";
	}

	/**
	 * Runs the change and goes back to the page with what happened: the result, or why it
	 * was refused.
	 */
	private static String saved(RedirectAttributes redirect, BindingResult result, Change change) {
		if (result.hasErrors()) {
			redirect.addFlashAttribute("error",
					"Check the form: " + result.getFieldErrors()
						.stream()
						.map((error) -> error.getField() + " " + error.getDefaultMessage())
						.toList());
		}
		else {
			try {
				redirect.addFlashAttribute("notice", change.apply());
			}
			catch (ErrorResponseException ex) {
				redirect.addFlashAttribute("error", ex.getBody().getDetail());
			}
		}
		return "redirect:/app/catalog";
	}

	@FunctionalInterface
	private interface Change {

		String apply();

	}

	record PublisherForm(@NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
			message = "letters, digits, dots, dashes and underscores") String name) {
	}

	record ExtensionForm(@NotNull Long publisherId, @NotBlank @Size(max = 200) String displayName) {
	}

	record ListingForm(@NotNull Long extensionId, @NotNull Store store, @NotBlank @Size(max = 200) String externalId) {
	}

}
