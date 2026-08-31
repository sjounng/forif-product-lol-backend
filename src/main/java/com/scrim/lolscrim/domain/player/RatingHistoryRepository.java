package com.scrim.lolscrim.domain.player;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

	List<RatingHistory> findByMatchId(Long matchId);

	List<RatingHistory> findByPlayerIdOrderByIdDesc(Long playerId);
}
