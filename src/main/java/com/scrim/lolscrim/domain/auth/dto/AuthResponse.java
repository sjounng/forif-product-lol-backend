package com.scrim.lolscrim.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		@JsonIgnore
		String refreshToken,
		UserResponse user) {

	public static AuthResponse of(String accessToken, long expiresIn, String refreshToken, UserResponse user) {
		return new AuthResponse(accessToken, "Bearer", expiresIn, refreshToken, user);
	}
}
