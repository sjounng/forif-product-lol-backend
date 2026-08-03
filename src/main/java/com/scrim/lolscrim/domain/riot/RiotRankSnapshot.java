package com.scrim.lolscrim.domain.riot;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

	@Enumerated(EnumType.STRING)
	@Column(name = "queue_type", nullable = false)
	private QueueType queueType;

	@Column(name = "season", length = 16)
	private String season;

	@Enumerated(EnumType.STRING)
	@Column(name = "tier", nullable = false)
	private Tier tier;

	@Enumerated(EnumType.STRING)
	@Column(name = "rank_division")
	private RankDivision rankDivision;

	/** smallint unsigned */
	@Column(name = "league_points", nullable = false)
	private short leaguePoints;

	/** smallint unsigned */
	@Column(name = "wins", nullable = false)
	private short wins;

	/** smallint unsigned */
	@Column(name = "losses", nullable = false)
	private short losses;

	@Column(name = "ladder_score", nullable = false)
	private int ladderScore;

	@Column(name = "captured_at", insertable = false, updatable = false)
	private LocalDateTime capturedAt;

	public static RiotRankSnapshot create(
			Long riotAccountId,
			QueueType queueType,
			Tier tier,
			RankDivision rankDivision,
			int leaguePoints,
			int wins,
			int losses,
			int ladderScore) {
		RiotRankSnapshot snapshot = new RiotRankSnapshot();
		snapshot.riotAccountId = riotAccountId;
		snapshot.queueType = queueType;
		snapshot.tier = tier;
		snapshot.rankDivision = rankDivision;
		snapshot.leaguePoints = (short) leaguePoints;
		snapshot.wins = (short) wins;
		snapshot.losses = (short) losses;
		snapshot.ladderScore = ladderScore;
		return snapshot;
	}
}
