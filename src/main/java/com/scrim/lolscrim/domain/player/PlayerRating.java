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
		PlayerRating playerRating = new PlayerRating();
		playerRating.playerId = playerId;
		playerRating.roomId = roomId;
		playerRating.rating = rating;
		playerRating.rd = (short) rd;
		playerRating.peakRating = rating;
		playerRating.seedSource = seedSource;
		playerRating.seedRating = rating;
		return playerRating;
	}

	/**
	 * 확정된 내전 한 판을 반영한다. DESIGN §4.2.
	 *
	 * 점수 계산 자체는 {@link GlickoCalculator} 가 하고, 여기서는 전적/연승만 같이 갱신한다.
	 *
	 * @param newRating 오프롤 감쇠(§4.3)까지 끝난 최종 rating
	 */
	public void applyMatchResult(int newRating, int newRd, boolean win, LocalDateTime playedAt) {
		rating = newRating;
		rd = (short) newRd;
		peakRating = Math.max(peakRating, newRating);
		gamesPlayed++;
		if (win) {
			wins++;
			winStreak = (short) (winStreak >= 0 ? winStreak + 1 : 1);
		} else {
			losses++;
			winStreak = (short) (winStreak <= 0 ? winStreak - 1 : -1);
		}
		lastPlayedAt = playedAt;
	}

}
