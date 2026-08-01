package com.scrim.lolscrim.domain.match.dto;

import java.util.List;

public record MatchOverviewResponse(
		Long sessionId,
		MatchScoreResponse score,
		MatchStartRequestResponse pendingStartRequest,
		List<MatchResponse> matches,
		boolean canRequestStart,
		boolean canFinishSession) {
}
