package com.scrim.lolscrim.domain.match;

import java.time.LocalDateTime;

import com.scrim.lolscrim.domain.session.ScrimSession;
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
@Table(name = "match_start_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchStartRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "scrim_session_id", nullable = false)
	private Long sessionId;

	@Column(name = "game_no", nullable = false)
	private Byte gameNo;

	@Column(name = "proposed_by_user_id", nullable = false)
	private Long proposedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "blue_team_side", nullable = false)
	private TeamSide blueTeamSide;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MatchStartRequestStatus status;

	@Column(name = "responded_by_user_id")
	private Long respondedByUserId;

	@Column(name = "accepted_match_id")
	private Long acceptedMatchId;

	@Column(name = "pending_session_id", insertable = false, updatable = false)
	private Long pendingSessionId;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "responded_at")
	private LocalDateTime respondedAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static MatchStartRequest propose(
			ScrimSession session,
			int gameNo,
			Long proposedByUserId,
			LocalDateTime now) {
		return propose(session, gameNo, proposedByUserId, TeamSide.BLUE, now);
	}

	public static MatchStartRequest propose(
			ScrimSession session,
			int gameNo,
			Long proposedByUserId,
			TeamSide blueTeamSide,
			LocalDateTime now) {
		MatchStartRequest request = new MatchStartRequest();
		request.sessionId = session.getId();
		request.gameNo = (byte) gameNo;
		request.proposedByUserId = proposedByUserId;
		request.blueTeamSide = blueTeamSide;
		request.status = MatchStartRequestStatus.PENDING;
		request.createdAt = now;
		request.updatedAt = now;
		return request;
	}

	public void accept(Long respondedByUserId, Long matchId, LocalDateTime now) {
		status = MatchStartRequestStatus.ACCEPTED;
		this.respondedByUserId = respondedByUserId;
		acceptedMatchId = matchId;
		respondedAt = now;
		updatedAt = now;
	}

	public void reject(Long respondedByUserId, LocalDateTime now) {
		status = MatchStartRequestStatus.REJECTED;
		this.respondedByUserId = respondedByUserId;
		respondedAt = now;
		updatedAt = now;
	}

	public void cancel(LocalDateTime now) {
		status = MatchStartRequestStatus.CANCELLED;
		respondedAt = now;
		updatedAt = now;
	}
}
