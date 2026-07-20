package com.scrim.lolscrim.domain.auth.dto;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		String refreshToken,
		UserResponse user) {

	public static AuthResponse of(String accessToken, long expiresIn, String refreshToken, UserResponse user) {
		return new AuthResponse(accessToken, "Bearer", expiresIn, refreshToken, user);
	}
}
