package com.scrim.lolscrim.domain.room;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.room.dto.CreateRoomRequest;
import com.scrim.lolscrim.domain.room.dto.RoomResponse;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

	private final RoomService roomService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RoomResponse createRoom(@AuthUserId Long userId, @Valid @RequestBody CreateRoomRequest request) {
		return roomService.createRoom(userId, request);
	}

	@GetMapping
	public List<RoomResponse> listRooms(@AuthUserId Long userId) {
		return roomService.listRooms(userId);
	}

	@GetMapping("/{roomId}")
	public RoomResponse getRoom(@AuthUserId Long userId, @PathVariable Long roomId) {
		return roomService.getRoom(userId, roomId);
	}
}
