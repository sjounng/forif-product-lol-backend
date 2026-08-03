package com.scrim.lolscrim.domain.player.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddRiotPlayerRequest(
		@NotBlank
		@Size(min = 3, max = 16)
		@Pattern(regexp = "^[^#]+$", message = "# 문자는 게임 이름에 입력할 수 없습니다.")
		String gameName,
		@NotBlank
		@Size(min = 3, max = 5)
		@Pattern(regexp = "^[\\p{L}\\p{N}]+$", message = "태그는 문자와 숫자만 입력할 수 있습니다.")
		String tagLine) {
}
