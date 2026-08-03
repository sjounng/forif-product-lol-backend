package com.scrim.lolscrim.domain.match;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MatchStartRequestRepository extends JpaRepository<MatchStartRequest, Long> {

	Optional<MatchStartRequest> findFirstBySessionIdAndStatusOrderByCreatedAtDesc(
			Long sessionId,
			MatchStartRequestStatus status);

	boolean existsBySessionIdAndStatus(Long sessionId, MatchStartRequestStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select request from MatchStartRequest request where request.id = :id")
	Optional<MatchStartRequest> findByIdForUpdate(@Param("id") Long id);
}
