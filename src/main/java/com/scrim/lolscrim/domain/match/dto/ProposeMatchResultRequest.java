package com.scrim.lolscrim.domain.match.dto;

import java.util.List;

import com.scrim.lolscrim.domain.session.TeamSide;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record ProposeMatchResultRequest(
		@NotNull TeamSide winnerSide,
		@Size(max = 32) String riotMatchId,
		@NotNull @Size(min = 10, max = 10) List<@Valid MatchParticipantStatsRequest> participantStats) {
}
