package com.scrim.lolscrim.domain.auth.password.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
		@NotBlank String resetToken,
		@NotBlank @Size(min = 8, max = 72) String newPassword) {
}
