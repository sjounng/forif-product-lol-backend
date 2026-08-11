package com.scrim.lolscrim.domain.group.dto;

public record PublicRoomResponse(
		Long id,
		String name,
		String description,
		String publicCode,
		boolean guestAdmissionEnabled,
		boolean entryPasswordProtected,
		long participantCount) {
}

