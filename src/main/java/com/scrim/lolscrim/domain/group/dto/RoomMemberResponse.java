package com.scrim.lolscrim.domain.group.dto;

import java.time.LocalDateTime;

import com.scrim.lolscrim.domain.group.GroupRole;
import com.scrim.lolscrim.domain.player.dto.RiotPlayerResponse;

public record RoomMemberResponse(
		Long membershipId,
		GroupUserResponse user,
		GroupRole role,
		LocalDateTime joinedAt,
		RiotPlayerResponse player) {
}
