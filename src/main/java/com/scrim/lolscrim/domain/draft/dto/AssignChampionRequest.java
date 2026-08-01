package com.scrim.lolscrim.domain.draft.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AssignChampionRequest(
		@NotNull @Positive Long playerId,
		@NotNull @Positive Integer championId,
		@NotNull @PositiveOrZero Integer expectedVersion) {
}
