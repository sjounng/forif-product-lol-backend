package com.scrim.lolscrim.domain.group;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.group.dto.CaptainInvitationResponse;
import com.scrim.lolscrim.global.auth.AuthUserId;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/group-invitations")
@RequiredArgsConstructor
public class RoomInvitationController {

	private final RoomInvitationService invitationService;

	@GetMapping
	public List<CaptainInvitationResponse> received(@AuthUserId Long userId) {
		return invitationService.getReceivedInvitations(userId);
	}

	@PostMapping("/{invitationId}/accept")
	public CaptainInvitationResponse accept(
			@AuthUserId Long userId,
			@PathVariable Long invitationId) {
		return invitationService.accept(userId, invitationId);
	}

	@PostMapping("/{invitationId}/reject")
	public CaptainInvitationResponse reject(
			@AuthUserId Long userId,
			@PathVariable Long invitationId) {
		return invitationService.reject(userId, invitationId);
	}

	@DeleteMapping("/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(
			@AuthUserId Long userId,
			@PathVariable Long invitationId) {
		invitationService.cancel(userId, invitationId);
	}
}

