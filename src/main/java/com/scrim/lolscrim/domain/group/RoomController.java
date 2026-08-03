package com.scrim.lolscrim.domain.group;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.group.dto.ChangeMemberRoleRequest;
import com.scrim.lolscrim.domain.group.dto.CaptainInvitationResponse;
import com.scrim.lolscrim.domain.group.dto.CreateRoomRequest;
import com.scrim.lolscrim.domain.group.dto.InviteCaptainRequest;
import com.scrim.lolscrim.domain.group.dto.RoomMemberResponse;
import com.scrim.lolscrim.domain.group.dto.RoomResponse;
import com.scrim.lolscrim.domain.group.dto.UpdateRoomRequest;
import com.scrim.lolscrim.domain.group.dto.JoinRoomRequest;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

	private final RoomService roomService;
	private final RoomInvitationService invitationService;

	@GetMapping
	public List<RoomResponse> getMyRooms(@AuthUserId Long userId) {
		return roomService.getMyRooms(userId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RoomResponse create(
			@AuthUserId Long userId,
			@Valid @RequestBody CreateRoomRequest request) {
		return roomService.createRoom(userId, request);
	}

	@PostMapping("/join/{publicCode}")
	public RoomResponse join(
			@AuthUserId Long userId,
			@PathVariable String publicCode,
			@Valid @RequestBody JoinRoomRequest request) {
		return roomService.joinRoom(userId, publicCode, request);
	}

	@GetMapping("/{roomId}")
	public RoomResponse get(
			@AuthUserId Long userId,
			@PathVariable Long roomId) {
		return roomService.getRoom(userId, roomId);
	}

	@PatchMapping("/{roomId}")
	public RoomResponse update(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@Valid @RequestBody UpdateRoomRequest request) {
		return roomService.updateRoom(userId, roomId, request);
	}

	@PostMapping("/{roomId}/public-code")
	public RoomResponse rotatePublicCode(
			@AuthUserId Long userId,
			@PathVariable Long roomId) {
		return roomService.rotatePublicCode(userId, roomId);
	}

	@GetMapping("/{roomId}/members")
	public List<RoomMemberResponse> getMembers(
			@AuthUserId Long userId,
			@PathVariable Long roomId) {
		return roomService.getMembers(userId, roomId);
	}

	@PatchMapping("/{roomId}/members/{memberUserId}")
	public RoomMemberResponse changeMemberRole(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@PathVariable Long memberUserId,
			@Valid @RequestBody ChangeMemberRoleRequest request) {
		return roomService.changeMemberRole(userId, roomId, memberUserId, request);
	}

	@PostMapping("/{roomId}/captain-invitations")
	@ResponseStatus(HttpStatus.CREATED)
	public CaptainInvitationResponse inviteCaptain(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@Valid @RequestBody InviteCaptainRequest request) {
		return invitationService.invite(userId, roomId, request.userId());
	}

	@DeleteMapping("/{roomId}/members/{memberUserId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@PathVariable Long memberUserId) {
		roomService.removeMember(userId, roomId, memberUserId);
	}

	@DeleteMapping("/{roomId}/members/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leaveRoom(@AuthUserId Long userId, @PathVariable Long roomId) {
		roomService.leaveRoom(userId, roomId);
	}

	@DeleteMapping("/{roomId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteRoom(@AuthUserId Long userId, @PathVariable Long roomId) {
		roomService.deleteRoom(userId, roomId);
	}
}
