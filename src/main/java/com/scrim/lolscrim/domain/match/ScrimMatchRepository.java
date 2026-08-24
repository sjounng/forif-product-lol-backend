package com.scrim.lolscrim.domain.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.scrim.lolscrim.domain.session.TeamSide;

import jakarta.persistence.LockModeType;

public interface ScrimMatchRepository extends JpaRepository<ScrimMatch, Long> {

	List<ScrimMatch> findAllBySessionIdOrderByGameNoAsc(Long sessionId);

	Optional<ScrimMatch> findFirstBySessionIdOrderByGameNoDesc(Long sessionId);

	boolean existsBySessionIdAndStatusIn(Long sessionId, Collection<MatchStatus> statuses);

	long countBySessionIdAndStatusAndWinnerSide(Long sessionId, MatchStatus status, TeamSide winnerSide);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select match from ScrimMatch match where match.id = :id")
	Optional<ScrimMatch> findByIdForUpdate(@Param("id") Long id);
}
