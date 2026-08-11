package com.scrim.lolscrim.domain.draft;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftRulesetStepRepository extends JpaRepository<DraftRulesetStep, DraftRulesetStepId> {

	List<DraftRulesetStep> findAllByRulesetIdOrderByStepNoAsc(String rulesetId);

	Optional<DraftRulesetStep> findByRulesetIdAndStepNo(String rulesetId, Byte stepNo);
}
