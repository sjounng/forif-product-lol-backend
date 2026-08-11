package com.scrim.lolscrim.domain.draft.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReadyDraftRequest(
		@NotNull @PositiveOrZero Integer expectedVersion) {
}
