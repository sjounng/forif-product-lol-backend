package com.scrim.lolscrim.domain.group;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.group.dto.GuestResponse;
import com.scrim.lolscrim.domain.group.dto.RenameGuestRequest;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms/{roomId}/guests")
@RequiredArgsConstructor
public class GuestAdminController {

	private final GuestAdmissionService guestAdmissionService;

	@GetMapping
	public List<GuestResponse> getGuests(
			@AuthUserId Long userId,
			@PathVariable Long roomId) {
		return guestAdmissionService.getGuests(userId, roomId);
	}

	@PatchMapping("/{guestId}")
	public GuestResponse rename(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@PathVariable Long guestId,
			@Valid @RequestBody RenameGuestRequest request) {
		return guestAdmissionService.renameGuest(userId, roomId, guestId, request);
	}

	@DeleteMapping("/{guestId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@PathVariable Long guestId) {
		guestAdmissionService.removeGuest(userId, roomId, guestId);
	}
}
