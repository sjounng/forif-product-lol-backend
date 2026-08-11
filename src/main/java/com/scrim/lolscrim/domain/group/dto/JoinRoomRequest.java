package com.scrim.lolscrim.domain.group.dto;

import jakarta.validation.constraints.Size;

public record JoinRoomRequest(@Size(max = 72) String entryPassword) {
}
