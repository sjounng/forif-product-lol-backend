package com.scrim.lolscrim.domain.player;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerLaneRatingRepository extends JpaRepository<PlayerLaneRating, PlayerLaneRatingId> {

	List<PlayerLaneRating> findByPlayerId(Long playerId);

	List<PlayerLaneRating> findByPlayerIdIn(List<Long> playerIds);
}
