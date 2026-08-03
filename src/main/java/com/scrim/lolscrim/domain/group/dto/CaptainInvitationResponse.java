package com.scrim.lolscrim.domain.group.dto;

import java.time.LocalDateTime;

import com.scrim.lolscrim.domain.group.CaptainInvitationStatus;

public record CaptainInvitationResponse(
		Long id,
		Long roomId,
		String roomName,
		GroupUserResponse inviter,
		GroupUserResponse invitee,
		CaptainInvitationStatus status,
		LocalDateTime expiresAt,
		LocalDateTime createdAt) {
}

