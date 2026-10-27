package com.mccprogrammer.extpulse.alerts;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

	List<AlertRule> findByUserIdOrderById(long userId);

	Optional<AlertRule> findByIdAndUserId(long id, long userId);

}
