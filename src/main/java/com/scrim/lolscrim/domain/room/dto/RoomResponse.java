package com.scrim.lolscrim.domain.room.dto;

import com.scrim.lolscrim.domain.room.Room;
import com.scrim.lolscrim.domain.room.RoomStatus;

public record RoomResponse(
		Long id,
		String name,
		String description,
		String publicCode,
		boolean guestCanDraft,
		int playerCount,
		RoomStatus status) {

	public static RoomResponse of(Room room, int playerCount) {
		return new RoomResponse(
				room.getId(), room.getName(), room.getDescription(), room.getPublicCode(),
				room.isGuestCanDraft(), playerCount, room.getStatus());
	}
}
