package com.mccprogrammer.extpulse.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mccprogrammer.extpulse.ApiProblem;
import com.mccprogrammer.extpulse.store.DiscoveredListing;
import com.mccprogrammer.extpulse.store.ListingNotFoundException;
import com.mccprogrammer.extpulse.store.Store;
import com.mccprogrammer.extpulse.store.StoreException;
import com.mccprogrammer.extpulse.store.StoreSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adds what a user tracks: publishers (found on every store at once), extensions and
 * listings. Store calls always happen before the transaction that saves the result, so no
 * database connection waits on the network.
 */
@Service
public class CatalogService {

	private static final Logger logger = LoggerFactory.getLogger(CatalogService.class);

	private final PublisherRepository publishers;

	private final ExtensionRepository extensions;

	private final ListingRepository listings;

	private final Map<Store, StoreSource> sources = new EnumMap<>(Store.class);

	private final TransactionTemplate transactions;

	CatalogService(PublisherRepository publishers, ExtensionRepository extensions, ListingRepository listings,
			List<StoreSource> sources, TransactionTemplate transactions) {
		this.publishers = publishers;
		this.extensions = extensions;
		this.listings = listings;
		this.transactions = transactions;
		sources.forEach((source) -> this.sources.put(source.store(), source));
	}

	/**
	 * Looks the publisher up on every store and saves one extension per extension found,
	 * with a listing for each store that has it.
	 */
	AddedPublisher addPublisher(long userId, String name) {
		if (this.publishers.existsByUserIdAndName(userId, name)) {
			throw ApiProblem.of(HttpStatus.CONFLICT, "You already added the publisher " + name);
		}
		List<DiscoveredListing> found = new ArrayList<>();
		for (StoreSource source : this.sources.values()) {
			try {
				found.addAll(source.discover(name));
			}
			catch (StoreException ex) {
				// The answer only says the store failed; the cause is for the log.
				logger.warn("Looking up publisher {} on {} failed", name, source.store(), ex);
				throw ApiProblem.of(HttpStatus.BAD_GATEWAY,
						source.store() + " could not be read; nothing was added. Try again later.", ex);
			}
		}
		if (found.isEmpty()) {
			throw ApiProblem.of(HttpStatus.NOT_FOUND, "No store has extensions from the publisher " + name);
		}
		for (DiscoveredListing listing : found) {
			requireUntracked(listing.store(), listing.externalId());
		}
		try {
			return this.transactions.execute((status) -> savePublisher(userId, name, found));
		}
		catch (DataIntegrityViolationException ex) {
			// Someone added the same publisher or listing between the checks and the
			// save.
			throw ApiProblem.of(HttpStatus.CONFLICT, "This publisher or one of its listings was just added", ex);
		}
	}

	private AddedPublisher savePublisher(long userId, String name, List<DiscoveredListing> found) {
		Publisher publisher = this.publishers.save(new Publisher(userId, name));
		Map<String, List<DiscoveredListing>> byExtension = new LinkedHashMap<>();
		for (DiscoveredListing listing : found) {
			byExtension.computeIfAbsent(listing.extensionKey(), (key) -> new ArrayList<>()).add(listing);
		}
		Set<String> usedNames = new HashSet<>();
		List<AddedExtension> added = new ArrayList<>();
		for (Map.Entry<String, List<DiscoveredListing>> entry : byExtension.entrySet()) {
			List<DiscoveredListing> group = entry.getValue();
			// Every store shows a name; the Marketplace's comes first (Store order).
			group.sort(Comparator.comparing(DiscoveredListing::store));
			String displayName = group.getFirst().displayName();
			if (!usedNames.add(displayName)) {
				// extensions are unique by (publisher, display name); keep both by
				// telling them apart.
				displayName = displayName + " (" + entry.getKey() + ")";
				usedNames.add(displayName);
			}
			Extension extension = this.extensions.save(new Extension(publisher.getId(), displayName));
			List<Listing> saved = group.stream()
				.map((listing) -> this.listings
					.save(new Listing(extension.getId(), listing.store(), listing.externalId(), listing.url())))
				.toList();
			added.add(new AddedExtension(extension, saved));
		}
		return new AddedPublisher(publisher, added);
	}

	/** An extension the stores can't find by publisher, such as a Chrome extension. */
	Extension addExtension(long userId, long publisherId, String displayName) {
		Publisher publisher = this.publishers.findById(publisherId)
			.filter((candidate) -> candidate.getUserId() == userId)
			.orElseThrow(() -> ApiProblem.of(HttpStatus.NOT_FOUND, "You have no publisher " + publisherId));
		try {
			return this.extensions.saveAndFlush(new Extension(publisher.getId(), displayName));
		}
		catch (DataIntegrityViolationException ex) {
			throw ApiProblem.of(HttpStatus.CONFLICT,
					publisher.getName() + " already has an extension called " + displayName, ex);
		}
	}

	/**
	 * Adds a listing by its ID on the store, after checking that the store has it.
	 */
	Listing addListing(long userId, long extensionId, Store store, String externalId) {
		Extension extension = requireOwnedExtension(userId, extensionId);
		requireUntracked(store, externalId);
		try {
			this.sources.get(store).fetchStats(externalId);
		}
		catch (ListingNotFoundException ex) {
			throw ApiProblem.of(HttpStatus.UNPROCESSABLE_CONTENT, store + " has no listing " + externalId, ex);
		}
		catch (IllegalArgumentException ex) {
			throw ApiProblem.of(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
		catch (StoreException ex) {
			throw ApiProblem.of(HttpStatus.BAD_GATEWAY, store + " could not be read; try again later.", ex);
		}
		try {
			return this.listings.saveAndFlush(new Listing(extension.getId(), store, externalId, null));
		}
		catch (DataIntegrityViolationException ex) {
			throw ApiProblem.of(HttpStatus.CONFLICT, externalId + " on " + store + " was just added", ex);
		}
	}

	List<Publisher> publishersOf(long userId) {
		return this.publishers.findByUserIdOrderByName(userId);
	}

	List<Extension> extensionsOf(long userId) {
		return this.extensions.findAllOwned(userId);
	}

	/**
	 * The extension, if it belongs to the user. Another user's extension gets the same
	 * 404 as a missing one, so IDs don't reveal what others track.
	 */
	public Extension requireOwnedExtension(long userId, long extensionId) {
		return this.extensions.findOwned(extensionId, userId)
			.orElseThrow(() -> ApiProblem.of(HttpStatus.NOT_FOUND, "You have no extension " + extensionId));
	}

	private void requireUntracked(Store store, String externalId) {
		// A listing has one owner (listings are unique by store and ID), so the same
		// extension can't be tracked by two accounts yet.
		if (this.listings.existsByStoreAndExternalId(store, externalId)) {
			throw ApiProblem.of(HttpStatus.CONFLICT, externalId + " on " + store + " is already tracked");
		}
	}

	record AddedPublisher(Publisher publisher, List<AddedExtension> extensions) {
	}

	record AddedExtension(Extension extension, List<Listing> listings) {
	}

}
