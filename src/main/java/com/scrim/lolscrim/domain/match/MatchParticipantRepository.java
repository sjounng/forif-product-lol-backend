package com.scrim.lolscrim.domain.match;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

	List<MatchParticipant> findAllByMatchId(Long matchId);

	List<MatchParticipant> findAllByMatchIdInOrderByMatchIdAscSideAscLaneAsc(Collection<Long> matchIds);
}
