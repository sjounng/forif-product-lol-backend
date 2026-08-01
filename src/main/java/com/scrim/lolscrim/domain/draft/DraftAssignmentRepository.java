package com.scrim.lolscrim.domain.draft;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftAssignmentRepository extends JpaRepository<DraftAssignment, Long> {

	List<DraftAssignment> findAllByDraftIdOrderBySideAscPlayerIdAsc(Long draftId);

	Optional<DraftAssignment> findByDraftIdAndPlayerId(Long draftId, Long playerId);

	Optional<DraftAssignment> findByDraftIdAndChampionId(Long draftId, Integer championId);
}
