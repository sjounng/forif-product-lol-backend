package com.scrim.lolscrim.domain.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 500) String description,
		Long opponentCaptainUserId,
		boolean guestAdmissionEnabled,
		@Size(max = 72) String entryPassword) {
}
