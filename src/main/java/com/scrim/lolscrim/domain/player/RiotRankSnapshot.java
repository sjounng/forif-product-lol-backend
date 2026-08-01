package com.scrim.lolscrim.domain.player;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "riot_rank_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiotRankSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "riot_account_id", nullable = false)
	private Long riotAccountId;

	@Column(name = "queue_type", nullable = false, length = 24)
	private String queueType;

	@Column(nullable = false, length = 16)
	private String tier;

	@Column(name = "rank_division", length = 3)
	private String division;

	@Column(name = "league_points", nullable = false)
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int leaguePoints;

	@Column(nullable = false)
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int wins;

	@Column(nullable = false)
	@JdbcTypeCode(SqlTypes.SMALLINT)
	private int losses;

	@Column(name = "ladder_score", nullable = false)
	private int ladderScore;

	@Column(name = "captured_at", nullable = false)
	private LocalDateTime capturedAt;

	public static RiotRankSnapshot create(
			Long riotAccountId,
			String tier,
			String division,
			int leaguePoints,
			int wins,
			int losses,
			LocalDateTime now) {
		RiotRankSnapshot snapshot = new RiotRankSnapshot();
		snapshot.riotAccountId = riotAccountId;
		snapshot.queueType = "RANKED_SOLO_5x5";
		snapshot.tier = tier == null ? "UNRANKED" : tier;
		snapshot.division = division;
		snapshot.leaguePoints = leaguePoints;
		snapshot.wins = wins;
		snapshot.losses = losses;
		snapshot.ladderScore = ladderScore(snapshot.tier, division, leaguePoints);
		snapshot.capturedAt = now;
		return snapshot;
	}

	private static int ladderScore(String tier, String division, int leaguePoints) {
		int tierBase = switch (tier) {
			case "IRON" -> 0;
			case "BRONZE" -> 400;
			case "SILVER" -> 800;
			case "GOLD" -> 1200;
			case "PLATINUM" -> 1600;
			case "EMERALD" -> 2000;
			case "DIAMOND" -> 2400;
			case "MASTER" -> 2800;
			case "GRANDMASTER" -> 3200;
			case "CHALLENGER" -> 3600;
			default -> 0;
		};
		int divisionScore = switch (division == null ? "" : division) {
			case "IV" -> 0;
			case "III" -> 100;
			case "II" -> 200;
			case "I" -> 300;
			default -> 0;
		};
		return tierBase + divisionScore + Math.max(0, leaguePoints);
	}
}
