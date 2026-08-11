package com.scrim.lolscrim.domain.draft;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.scrim.lolscrim.domain.session.TeamSide;

public interface DraftHoverRepository extends JpaRepository<DraftHover, DraftHoverId> {

	Optional<DraftHover> findByDraftIdAndSide(Long draftId, TeamSide side);

	Optional<DraftHover> findFirstByDraftIdOrderByUpdatedAtDesc(Long draftId);

	void deleteByDraftIdAndSide(Long draftId, TeamSide side);
}
