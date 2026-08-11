package com.scrim.lolscrim.domain.session.dto;

import com.scrim.lolscrim.domain.session.TeamSide;

public record SessionViewerResponse(
		TeamSide captainSide,
		boolean canReview,
		boolean canCancel,
		boolean canCreateMatch) {
}
