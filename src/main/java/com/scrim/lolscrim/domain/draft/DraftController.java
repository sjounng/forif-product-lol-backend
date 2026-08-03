package com.scrim.lolscrim.domain.draft;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.draft.dto.AssignChampionRequest;
import com.scrim.lolscrim.domain.draft.dto.ConfirmAssignmentRequest;
import com.scrim.lolscrim.domain.draft.dto.DraftStateResponse;
import com.scrim.lolscrim.domain.draft.dto.LockDraftRequest;
import com.scrim.lolscrim.domain.draft.dto.HoverDraftRequest;
import com.scrim.lolscrim.domain.draft.dto.ReadyDraftRequest;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

	private final DraftService draftService;

	@GetMapping("/{draftId}")
	public DraftStateResponse getDraft(
			@AuthUserId Long userId,
			@PathVariable Long draftId) {
		return draftService.getDraft(userId, draftId);
	}

	@PostMapping("/{draftId}/ready")
	public DraftStateResponse ready(
			@AuthUserId Long userId,
			@PathVariable Long draftId,
			@Valid @RequestBody ReadyDraftRequest request) {
		return draftService.ready(userId, draftId, request);
	}

	@PostMapping("/{draftId}/locks")
	public DraftStateResponse lock(
			@AuthUserId Long userId,
			@PathVariable Long draftId,
			@Valid @RequestBody LockDraftRequest request) {
		return draftService.lock(userId, draftId, request);
	}

	@PostMapping("/{draftId}/hover")
	public DraftStateResponse hover(
			@AuthUserId Long userId,
			@PathVariable Long draftId,
			@Valid @RequestBody HoverDraftRequest request) {
		return draftService.hover(userId, draftId, request);
	}

	@PutMapping("/{draftId}/assignments")
	public DraftStateResponse assign(
			@AuthUserId Long userId,
			@PathVariable Long draftId,
			@Valid @RequestBody AssignChampionRequest request) {
		return draftService.assign(userId, draftId, request);
	}

	@PostMapping("/{draftId}/assignments/confirm")
	public DraftStateResponse confirmAssignment(
			@AuthUserId Long userId,
			@PathVariable Long draftId,
			@Valid @RequestBody ConfirmAssignmentRequest request) {
		return draftService.confirmAssignment(userId, draftId, request);
	}
}
