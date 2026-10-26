package com.mccprogrammer.extpulse.catalog;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ExtensionRepository extends JpaRepository<Extension, Long> {

	/** The extension, only if it belongs to one of the user's publishers. */
	@Query("""
			select e from Extension e
			where e.id = :id and e.publisherId in (select p.id from Publisher p where p.userId = :userId)""")
	Optional<Extension> findOwned(long id, long userId);

}
