package com.scrim.lolscrim.domain.match.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.scrim.lolscrim.domain.match.MatchStatus;
import com.scrim.lolscrim.domain.match.ScrimMatch;
import com.scrim.lolscrim.domain.session.TeamSide;

public record MatchResponse(
		Long id,
		Long sessionId,
		int gameNo,
		MatchStatus status,
		TeamSide blueTeamSide,
		String blueTeamName,
		String redTeamName,
		TeamSide winnerSide,
		TeamSide proposedWinnerSide,
		Long resultProposedByUserId,
		String riotMatchId,
		Long draftId,
		LocalDateTime startedAt,
		LocalDateTime endedAt,
		LocalDateTime createdAt,
		List<MatchParticipantResponse> participants,
		List<MatchDraftActionResponse> draftActions,
		boolean canProposeResult,
		boolean canReviewResult) {

	public static MatchResponse from(
			ScrimMatch match,
			Long draftId,
			Long viewerUserId,
			boolean viewerIsCaptain) {
		return from(match, draftId, viewerUserId, viewerIsCaptain, "BLUE 팀", "RED 팀", List.of());
	}

	public static MatchResponse from(
			ScrimMatch match,
			Long draftId,
			Long viewerUserId,
			boolean viewerIsCaptain,
			String blueTeamName,
			String redTeamName) {
		return from(match, draftId, viewerUserId, viewerIsCaptain, blueTeamName, redTeamName, List.of());
	}

	public static MatchResponse from(
			ScrimMatch match,
			Long draftId,
			Long viewerUserId,
			boolean viewerIsCaptain,
			String blueTeamName,
			String redTeamName,
			List<MatchParticipantResponse> participants) {
		return from(
				match,
				draftId,
				viewerUserId,
				viewerIsCaptain,
				blueTeamName,
				redTeamName,
				participants,
				List.of());
	}

	public static MatchResponse from(
			ScrimMatch match,
			Long draftId,
			Long viewerUserId,
			boolean viewerIsCaptain,
			String blueTeamName,
			String redTeamName,
			List<MatchParticipantResponse> participants,
			List<MatchDraftActionResponse> draftActions) {
		boolean resultPending = match.getStatus() == MatchStatus.RESULT_PENDING;
		return new MatchResponse(
				match.getId(),
				match.getSessionId(),
				match.getGameNo().intValue(),
				match.getStatus(),
				match.getBlueTeamSide(),
				blueTeamName,
				redTeamName,
				match.getWinnerSide(),
				match.getProposedWinnerSide(),
				match.getResultProposedByUserId(),
				match.getRiotMatchId(),
				draftId,
				match.getStartedAt(),
				match.getEndedAt(),
				match.getCreatedAt(),
				List.copyOf(participants),
				List.copyOf(draftActions),
				viewerIsCaptain && (match.getStatus() == MatchStatus.LIVE
						|| match.getStatus() == MatchStatus.RESULT_DISPUTED),
				viewerIsCaptain && resultPending
						&& !viewerUserId.equals(match.getResultProposedByUserId()));
	}
}
