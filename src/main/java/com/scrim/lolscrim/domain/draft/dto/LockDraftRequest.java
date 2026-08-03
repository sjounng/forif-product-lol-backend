package com.scrim.lolscrim.domain.draft.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record LockDraftRequest(
		@NotNull @Min(1) Integer stepNo,
		@NotNull @Positive Integer championId,
		@Positive Long playerId,
		@NotNull @PositiveOrZero Integer expectedVersion) {
}
