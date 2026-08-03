package com.scrim.lolscrim.domain.draft;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.scrim.lolscrim.domain.session.TeamSide;

public interface DraftActionRepository extends JpaRepository<DraftAction, Long> {

	List<DraftAction> findAllByDraftIdOrderByStepNoAsc(Long draftId);

	List<DraftAction> findAllByDraftIdInOrderByDraftIdAscStepNoAsc(Collection<Long> draftIds);

	boolean existsByDraftIdAndChampionId(Long draftId, Integer championId);

	long countByDraftIdAndSideAndActionType(Long draftId, TeamSide side, DraftActionType actionType);
}
