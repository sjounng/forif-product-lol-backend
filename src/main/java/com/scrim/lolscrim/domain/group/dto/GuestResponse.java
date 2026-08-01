package com.scrim.lolscrim.domain.group.dto;

import java.time.LocalDateTime;

import com.scrim.lolscrim.domain.group.GuestSession;

public record GuestResponse(
		Long id,
		String nickname,
		boolean active,
		boolean banned,
		LocalDateTime joinedAt) {

	public static GuestResponse from(GuestSession guest, LocalDateTime now) {
		return new GuestResponse(
				guest.getId(),
				guest.getNickname(),
				guest.isUsable(now),
				guest.isBanned(),
				guest.getCreatedAt());
	}
}

