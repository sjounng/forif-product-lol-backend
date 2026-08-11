package com.scrim.lolscrim.domain.draft;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftEventRepository extends JpaRepository<DraftEvent, Long> {

	List<DraftEvent> findAllByDraftIdAndSeqGreaterThanOrderBySeqAsc(Long draftId, Integer seq);
}
