package com.scrim.lolscrim.domain.match;

import java.time.LocalDateTime;

import com.scrim.lolscrim.domain.session.TeamSide;

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
@Table(name = "matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScrimMatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "scrim_session_id", nullable = false)
	private Long sessionId;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "game_no", nullable = false)
	private Byte gameNo;

	@Column(name = "balance_candidate_id")
	private Long balanceCandidateId;

	@Column(name = "is_manual_team", nullable = false)
	private boolean manualTeam;

	@Enumerated(EnumType.STRING)
	@Column(name = "blue_team_side", nullable = false)
	private TeamSide blueTeamSide;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MatchStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "winner_side")
	private TeamSide winnerSide;

	@Column(name = "result_proposed_by_user_id")
	private Long resultProposedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "proposed_winner_side")
	private TeamSide proposedWinnerSide;

	@Column(name = "result_proposed_at")
	private LocalDateTime resultProposedAt;

	@Column(name = "result_confirmed_at")
	private LocalDateTime resultConfirmedAt;

	@Column(name = "riot_match_id", length = 32)
	private String riotMatchId;

	@Column(name = "duration_sec")
	private Integer durationSec;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	@Column(name = "rating_applied", nullable = false)
	private boolean ratingApplied;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static ScrimMatch createDrafting(
			Long sessionId,
			Long roomId,
			int gameNo,
			LocalDateTime now) {
		return createDrafting(sessionId, roomId, gameNo, TeamSide.BLUE, now);
	}

	public static ScrimMatch createDrafting(
			Long sessionId,
			Long roomId,
			int gameNo,
			TeamSide blueTeamSide,
			LocalDateTime now) {
		ScrimMatch match = new ScrimMatch();
		match.sessionId = sessionId;
		match.roomId = roomId;
		match.gameNo = (byte) gameNo;
		match.manualTeam = true;
		match.blueTeamSide = blueTeamSide;
		match.status = MatchStatus.DRAFTING;
		match.createdAt = now;
		match.updatedAt = now;
		return match;
	}

	public TeamSide sessionTeamForMatchSide(TeamSide matchSide) {
		return matchSide == TeamSide.BLUE ? blueTeamSide : opposite(blueTeamSide);
	}

	public TeamSide matchSideForSessionTeam(TeamSide sessionTeamSide) {
		return sessionTeamSide == blueTeamSide ? TeamSide.BLUE : TeamSide.RED;
	}

	public TeamSide winningSessionTeamSide() {
		return winnerSide == null ? null : sessionTeamForMatchSide(winnerSide);
	}

	private TeamSide opposite(TeamSide side) {
		return side == TeamSide.BLUE ? TeamSide.RED : TeamSide.BLUE;
	}

	public void markReadyToPlay(LocalDateTime now) {
		status = MatchStatus.READY_TO_PLAY;
		updatedAt = now;
	}

	public void start(LocalDateTime now) {
		status = MatchStatus.LIVE;
		startedAt = now;
		updatedAt = now;
	}

	public void proposeResult(
			Long userId,
			TeamSide winnerSide,
			String riotMatchId,
			LocalDateTime now) {
		status = MatchStatus.RESULT_PENDING;
		resultProposedByUserId = userId;
		proposedWinnerSide = winnerSide;
		this.riotMatchId = riotMatchId == null || riotMatchId.isBlank() ? null : riotMatchId.trim();
		resultProposedAt = now;
		updatedAt = now;
	}

	public void disputeResult(LocalDateTime now) {
		status = MatchStatus.RESULT_DISPUTED;
		updatedAt = now;
	}

	public void complete(LocalDateTime now) {
		status = MatchStatus.COMPLETED;
		winnerSide = proposedWinnerSide;
		resultConfirmedAt = now;
		endedAt = now;
		updatedAt = now;
	}
}
