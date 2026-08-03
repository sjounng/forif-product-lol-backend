package com.scrim.lolscrim.domain.player;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

/** player_lane_ratings 복합 PK (player_id, lane). */
public class PlayerLaneRatingId implements Serializable {

	private Long playerId;

	@Enumerated(EnumType.STRING)
	private Lane lane;

	protected PlayerLaneRatingId() {
	}

	public PlayerLaneRatingId(Long playerId, Lane lane) {
		this.playerId = playerId;
		this.lane = lane;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PlayerLaneRatingId other)) {
			return false;
		}
		return Objects.equals(playerId, other.playerId) && lane == other.lane;
	}

	@Override
	public int hashCode() {
		return Objects.hash(playerId, lane);
	}
}
