package com.scrim.lolscrim.domain.session;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionTeamRepository extends JpaRepository<SessionTeam, Long> {

	List<SessionTeam> findAllBySessionIdOrderBySideAsc(Long sessionId);

	Optional<SessionTeam> findBySessionIdAndSide(Long sessionId, TeamSide side);
}
