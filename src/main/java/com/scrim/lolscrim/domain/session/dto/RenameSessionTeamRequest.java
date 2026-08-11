package com.scrim.lolscrim.domain.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameSessionTeamRequest(
		@NotBlank @Size(max = 30) String teamName) {
}
