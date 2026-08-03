package com.scrim.lolscrim.domain.user.dto;

import com.scrim.lolscrim.domain.user.User;

public record UserSearchResponse(Long id, String displayName, String avatarUrl) {

	public static UserSearchResponse from(User user) {
		return new UserSearchResponse(user.getId(), user.getDisplayName(), user.getAvatarUrl());
	}
}

