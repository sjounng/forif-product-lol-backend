package com.scrim.lolscrim.domain.player;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

	@Column(nullable = false)
	private int rating;

	@Column(nullable = false)
	private short rd;

	@Column(name = "peak_rating", nullable = false)
	private int peakRating;

	@Column(name = "games_played", nullable = false)
	private int gamesPlayed;

	@Column(nullable = false)
	private int wins;

	@Column(nullable = false)
	private int losses;

	@Column(name = "win_streak", nullable = false)
	private short winStreak;

	@Column(name = "seed_source", nullable = false)
	private String seedSource;

	@Column(name = "is_locked", nullable = false)
	private boolean locked;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static PlayerRating initial(Long playerId, Long roomId, LocalDateTime now) {
		PlayerRating rating = new PlayerRating();
		rating.playerId = playerId;
		rating.roomId = roomId;
		rating.rating = 1500;
		rating.rd = 350;
		rating.peakRating = 1500;
		rating.seedSource = "DEFAULT";
		rating.updatedAt = now;
		return rating;
	}
}
