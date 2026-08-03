package com.scrim.lolscrim.domain.group.dto;

import com.scrim.lolscrim.domain.user.User;

public record GroupUserResponse(Long id, String displayName, String avatarUrl) {

	public static GroupUserResponse from(User user) {
		return new GroupUserResponse(user.getId(), user.getDisplayName(), user.getAvatarUrl());
	}
}

