package com.scrim.lolscrim.domain.auth.dto;

import com.scrim.lolscrim.domain.user.User;

public record UserResponse(Long id, String email, String displayName, String avatarUrl) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
	}
}
