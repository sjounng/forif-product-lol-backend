package com.scrim.lolscrim.domain.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestEntryRequest(
		@NotBlank @Size(max = 50) String nickname,
		@Size(max = 72) String entryPassword) {
}

