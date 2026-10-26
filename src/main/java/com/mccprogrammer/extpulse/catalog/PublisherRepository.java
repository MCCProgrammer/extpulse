package com.mccprogrammer.extpulse.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PublisherRepository extends JpaRepository<Publisher, Long> {

	boolean existsByUserIdAndName(long userId, String name);

	List<Publisher> findByUserIdOrderByName(long userId);

}
