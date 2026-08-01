package com.scrim.lolscrim.domain.session.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.scrim.lolscrim.domain.session.FearlessMode;
import com.scrim.lolscrim.domain.session.MatchFormat;
import com.scrim.lolscrim.domain.session.SessionStatus;

public record SessionResponse(
		Long id,
		Long roomId,
		String name,
		MatchFormat matchFormat,
		FearlessMode fearlessMode,
		SessionStatus status,
		boolean ratingEnabled,
		int gameCount,
		String rejectionReason,
		LocalDateTime proposedAt,
		LocalDateTime confirmedAt,
		LocalDateTime createdAt,
		List<SessionTeamResponse> teams,
		SessionViewerResponse viewer) {
}
