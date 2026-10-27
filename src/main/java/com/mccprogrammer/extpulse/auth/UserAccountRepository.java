package com.mccprogrammer.extpulse.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	// lower() on both sides, matching the unique index on lower(email). A derived
	// findByEmailIgnoreCase would compare upper() and couldn't use that index.
	@Query("select a from UserAccount a where lower(a.email) = lower(:email)")
	Optional<UserAccount> findByEmail(String email);

	Optional<UserAccount> findByPublicSlug(String publicSlug);

}
