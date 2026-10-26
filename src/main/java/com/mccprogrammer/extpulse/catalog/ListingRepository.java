package com.mccprogrammer.extpulse.catalog;

import com.mccprogrammer.extpulse.store.Store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {

	boolean existsByStoreAndExternalId(Store store, String externalId);

}
