package com.scrim.lolscrim.domain.session;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ScrimSessionRepository extends JpaRepository<ScrimSession, Long> {

	List<ScrimSession> findAllByRoomIdOrderByCreatedAtDesc(Long roomId);

	boolean existsByRoomIdAndStatusIn(Long roomId, Collection<SessionStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select session from ScrimSession session where session.id = :sessionId")
	Optional<ScrimSession> findByIdForUpdate(@Param("sessionId") Long sessionId);
}
