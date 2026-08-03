package com.scrim.lolscrim.domain.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DraftRepository extends JpaRepository<Draft, Long> {

	Optional<Draft> findByMatchId(Long matchId);

	List<Draft> findAllByMatchIdIn(Collection<Long> matchIds);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select draft from Draft draft where draft.id = :id")
	Optional<Draft> findByIdForUpdate(@Param("id") Long id);

	List<Draft> findAllByStatusAndAssignmentDeadlineAtLessThanEqual(
			DraftStatus status,
			java.time.LocalDateTime deadline);

	List<Draft> findAllByStatusAndTurnDeadlineAtLessThanEqual(
			DraftStatus status,
			java.time.LocalDateTime deadline);
}
