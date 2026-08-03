package com.scrim.lolscrim.domain.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 라인별 점수 + lanePool 숙련도. DESIGN.md §4.2(라인별 Glicko) / §5.1(밸런싱 가중치).
 * {@code selfProficiency} 0~5 가 곧 DESIGN 의 lanePool 값이다 (0 = 배정 금지).
 */
@Entity
@Table(name = "player_lane_ratings")
@IdClass(PlayerLaneRatingId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerLaneRating {

	@Id
	@Column(name = "player_id")
	private Long playerId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "lane")
	private Lane lane;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "rating", nullable = false)
	private int rating;

	/** smallint unsigned */
	@Column(name = "rd", nullable = false)
	private short rd;

	@Column(name = "games_played", nullable = false)
	private int gamesPlayed;

	@Column(name = "wins", nullable = false)
	private int wins;

	@Column(name = "losses", nullable = false)
	private int losses;

	/** tinyint unsigned. 0=배정 금지, 1~5. DESIGN §5.1 의 lanePool. */
	@Column(name = "self_proficiency", nullable = false)
	private byte selfProficiency;

	public static PlayerLaneRating seed(
			Long playerId, Lane lane, Long roomId, int rating, int rd, int selfProficiency) {
		PlayerLaneRating plr = new PlayerLaneRating();
		plr.playerId = playerId;
		plr.lane = lane;
		plr.roomId = roomId;
		plr.rating = rating;
		plr.rd = (short) rd;
		plr.selfProficiency = (byte) selfProficiency;
		return plr;
	}

	public void updateProficiency(int selfProficiency) {
		this.selfProficiency = (byte) selfProficiency;
	}
}
