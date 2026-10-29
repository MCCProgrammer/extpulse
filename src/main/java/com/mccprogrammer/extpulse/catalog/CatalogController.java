package com.mccprogrammer.extpulse.catalog;

import java.util.List;

import com.mccprogrammer.extpulse.auth.AuthenticatedAccount;
import com.mccprogrammer.extpulse.catalog.CatalogService.AddedPublisher;
import com.mccprogrammer.extpulse.store.Store;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CatalogController {

	private final CatalogService catalog;

	CatalogController(CatalogService catalog) {
		this.catalog = catalog;
	}

	/** Finds the publisher on every store and tracks all its extensions. */
	@PostMapping("/api/publishers")
	@ResponseStatus(HttpStatus.CREATED)
	PublisherResponse addPublisher(@AuthenticationPrincipal Jwt token,
			@Valid @RequestBody AddPublisherRequest request) {
		return PublisherResponse.of(this.catalog.addPublisher(AuthenticatedAccount.id(token), request.name()));
	}

	@GetMapping("/api/publishers")
	List<PublisherSummary> publishers(@AuthenticationPrincipal Jwt token) {
		return this.catalog.publishersOf(AuthenticatedAccount.id(token))
			.stream()
			.map((publisher) -> new PublisherSummary(publisher.getId(), publisher.getName()))
			.toList();
	}

	/** An extension the stores can't find by publisher, such as a Chrome extension. */
	@PostMapping("/api/extensions")
	@ResponseStatus(HttpStatus.CREATED)
	ExtensionResponse addExtension(@AuthenticationPrincipal Jwt token,
			@Valid @RequestBody AddExtensionRequest request) {
		Extension extension = this.catalog.addExtension(AuthenticatedAccount.id(token), request.publisherId(),
				request.displayName());
		return new ExtensionResponse(extension.getId(), extension.getDisplayName(), List.of());
	}

	/** Adds a listing by its ID on the store, for example a Chrome Web Store item ID. */
	@PostMapping("/api/extensions/{id}/listings")
	@ResponseStatus(HttpStatus.CREATED)
	ListingResponse addListing(@AuthenticationPrincipal Jwt token, @PathVariable long id,
			@Valid @RequestBody AddListingRequest request) {
		return ListingResponse
			.of(this.catalog.addListing(AuthenticatedAccount.id(token), id, request.store(), request.externalId()));
	}

	/** Stops tracking the extension and deletes its numbers and reviews. */
	@DeleteMapping("/api/extensions/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeExtension(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
		this.catalog.removeExtension(AuthenticatedAccount.id(token), id);
	}

	record AddPublisherRequest(@NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
			message = "letters, digits, dots, dashes and underscores") String name) {
	}

	record AddExtensionRequest(@NotNull Long publisherId, @NotBlank @Size(max = 200) String displayName) {
	}

	record AddListingRequest(@NotNull Store store, @NotBlank @Size(max = 200) String externalId) {
	}

	record PublisherSummary(long id, String name) {
	}

	record PublisherResponse(long id, String name, List<ExtensionResponse> extensions) {

		static PublisherResponse of(AddedPublisher added) {
			return new PublisherResponse(added.publisher().getId(), added.publisher().getName(),
					added.extensions()
						.stream()
						.map((extension) -> new ExtensionResponse(extension.extension().getId(),
								extension.extension().getDisplayName(),
								extension.listings().stream().map(ListingResponse::of).toList()))
						.toList());
		}

	}

	record ExtensionResponse(long id, String displayName, List<ListingResponse> listings) {
	}

	record ListingResponse(long id, Store store, String externalId, String url) {

		static ListingResponse of(Listing listing) {
			return new ListingResponse(listing.getId(), listing.getStore(), listing.getExternalId(), listing.getUrl());
		}

	}

}
