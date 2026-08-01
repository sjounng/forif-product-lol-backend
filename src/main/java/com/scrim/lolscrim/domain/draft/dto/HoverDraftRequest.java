package com.scrim.lolscrim.domain.draft.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record HoverDraftRequest(
		@NotNull @Min(1) Integer stepNo,
		@Positive Integer championId,
		@NotNull @PositiveOrZero Integer expectedVersion) {
}
