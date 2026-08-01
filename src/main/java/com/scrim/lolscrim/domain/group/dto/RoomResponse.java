package com.scrim.lolscrim.domain.group.dto;

import java.time.LocalDateTime;

import com.scrim.lolscrim.domain.group.CaptainInvitationStatus;
import com.scrim.lolscrim.domain.group.GroupRole;
import com.scrim.lolscrim.domain.group.RoomStatus;

public record RoomResponse(
		Long id,
		String name,
		String description,
		String publicCode,
		boolean guestAdmissionEnabled,
		boolean entryPasswordProtected,
		RoomStatus status,
		long participantCount,
		long sessionCount,
		long matchCount,
		GroupUserResponse owner,
		GroupUserResponse opponentCaptain,
		CaptainInvitationStatus captainInvitationStatus,
		GroupRole myRole,
		LocalDateTime createdAt) {
}

