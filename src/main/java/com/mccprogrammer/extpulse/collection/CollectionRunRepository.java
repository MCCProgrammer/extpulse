package com.mccprogrammer.extpulse.collection;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface CollectionRunRepository extends JpaRepository<CollectionRun, Long> {

	List<CollectionRun> findTop14ByOrderByStartedAtDesc();

}
