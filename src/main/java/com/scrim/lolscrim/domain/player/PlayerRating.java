package com.scrim.lolscrim.domain.player;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PK가 player_id 그 자체다 (players 와 1:1, 별도 auto-increment 없음). */
@Entity
@Table(name = "player_ratings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerRating {

	@Id
	@Column(name = "player_id")
	private Long playerId;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "rating", nullable = false)
	private int rating;

	/** smallint unsigned */
	@Column(name = "rd", nullable = false)
	private short rd;

	@Column(name = "peak_rating", nullable = false)
	private int peakRating;

	@Column(name = "games_played", nullable = false)
	private int gamesPlayed;

	@Column(name = "wins", nullable = false)
	private int wins;

	@Column(name = "losses", nullable = false)
	private int losses;

	/** smallint (음수면 연패) */
	@Column(name = "win_streak", nullable = false)
	private short winStreak;

	@Enumerated(EnumType.STRING)
	@Column(name = "seed_source", nullable = false)
	private SeedSource seedSource;

	@Column(name = "seed_rating")
	private Integer seedRating;

	@Column(name = "is_locked", nullable = false)
	private boolean locked;

	@Column(name = "last_played_at")
	private LocalDateTime lastPlayedAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public static PlayerRating seed(Long playerId, Long roomId, int rating, int rd, SeedSource seedSource) {
		PlayerRating pr = new PlayerRating();
		pr.playerId = playerId;
		pr.roomId = roomId;
		pr.rating = rating;
		pr.rd = (short) rd;
		pr.peakRating = rating;
		pr.seedSource = seedSource;
		pr.seedRating = rating;
		return pr;
	}
}
