package com.scrim.lolscrim.domain.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddPlayerRequest(
		@NotBlank @Size(max = 50) String displayName,
		/** "GameName#TAG" 형식. 조회 실패해도 등록은 되고 riotAccount 만 비어있게 된다. */
		@NotBlank String riotId) {
}
