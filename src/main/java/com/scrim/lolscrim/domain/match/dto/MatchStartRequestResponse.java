package com.scrim.lolscrim.domain.match.dto;

import java.time.LocalDateTime;

import com.scrim.lolscrim.domain.group.dto.GroupUserResponse;
import com.scrim.lolscrim.domain.match.MatchStartRequest;
import com.scrim.lolscrim.domain.match.MatchStartRequestStatus;
import com.scrim.lolscrim.domain.session.TeamSide;

public record MatchStartRequestResponse(
		Long id,
		Long sessionId,
		int gameNo,
		GroupUserResponse proposedBy,
		TeamSide blueTeamSide,
		String blueTeamName,
		String redTeamName,
		MatchStartRequestStatus status,
		LocalDateTime createdAt,
		boolean canReview,
		boolean canCancel) {

	public static MatchStartRequestResponse from(
			MatchStartRequest request,
			GroupUserResponse proposedBy,
			Long viewerUserId,
			boolean viewerIsCaptain) {
		return from(request, proposedBy, viewerUserId, viewerIsCaptain, "BLUE 팀", "RED 팀");
	}

	public static MatchStartRequestResponse from(
			MatchStartRequest request,
			GroupUserResponse proposedBy,
			Long viewerUserId,
			boolean viewerIsCaptain,
			String blueTeamName,
			String redTeamName) {
		return new MatchStartRequestResponse(
				request.getId(),
				request.getSessionId(),
				request.getGameNo().intValue(),
				proposedBy,
				request.getBlueTeamSide(),
				blueTeamName,
				redTeamName,
				request.getStatus(),
				request.getCreatedAt(),
				request.getStatus() == MatchStartRequestStatus.PENDING
						&& viewerIsCaptain
						&& !request.getProposedByUserId().equals(viewerUserId),
				request.getStatus() == MatchStartRequestStatus.PENDING
						&& request.getProposedByUserId().equals(viewerUserId));
	}
}
