package com.scrim.lolscrim.domain.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameGuestRequest(@NotBlank @Size(max = 50) String nickname) {
}

