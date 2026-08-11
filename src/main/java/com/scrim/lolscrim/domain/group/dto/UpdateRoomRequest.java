package com.scrim.lolscrim.domain.group.dto;

import jakarta.validation.constraints.Size;

public record UpdateRoomRequest(
		@Size(max = 100) String name,
		@Size(max = 500) String description,
		Boolean guestAdmissionEnabled,
		@Size(max = 72) String entryPassword) {
}

