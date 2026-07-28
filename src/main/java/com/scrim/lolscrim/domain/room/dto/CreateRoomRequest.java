package com.scrim.lolscrim.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 500) String description,
		@NotBlank @Size(min = 4, max = 32) String entryCode) {
}
