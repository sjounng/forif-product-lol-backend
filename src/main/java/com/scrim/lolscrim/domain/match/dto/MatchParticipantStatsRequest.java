package com.scrim.lolscrim.domain.match.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MatchParticipantStatsRequest(
		@NotNull Long playerId,
		@Min(0) @Max(65535) int kills,
		@Min(0) @Max(65535) int deaths,
		@Min(0) @Max(65535) int assists) {
}
