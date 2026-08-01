package com.scrim.lolscrim.domain.session;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.session.dto.CreateSessionRequest;
import com.scrim.lolscrim.domain.session.dto.RejectSessionRequest;
import com.scrim.lolscrim.domain.session.dto.RenameSessionTeamRequest;
import com.scrim.lolscrim.domain.session.dto.SessionResponse;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionController {

	private final SessionService sessionService;

	@GetMapping("/rooms/{roomId}/sessions")
	public List<SessionResponse> getSessions(
			@AuthUserId Long userId,
			@PathVariable Long roomId) {
		return sessionService.getSessions(userId, roomId);
	}

	@PostMapping("/rooms/{roomId}/sessions")
	@ResponseStatus(HttpStatus.CREATED)
	public SessionResponse create(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@Valid @RequestBody CreateSessionRequest request) {
		return sessionService.createSession(userId, roomId, request);
	}

	@GetMapping("/sessions/{sessionId}")
	public SessionResponse get(
			@AuthUserId Long userId,
			@PathVariable Long sessionId) {
		return sessionService.getSession(userId, sessionId);
	}

	@PostMapping("/sessions/{sessionId}/accept")
	public SessionResponse accept(
			@AuthUserId Long userId,
			@PathVariable Long sessionId) {
		return sessionService.accept(userId, sessionId);
	}

	@PostMapping("/sessions/{sessionId}/reject")
	public SessionResponse reject(
			@AuthUserId Long userId,
			@PathVariable Long sessionId,
			@Valid @RequestBody RejectSessionRequest request) {
		return sessionService.reject(userId, sessionId, request.reason());
	}

	@PostMapping("/sessions/{sessionId}/cancel")
	public SessionResponse cancel(
			@AuthUserId Long userId,
			@PathVariable Long sessionId) {
		return sessionService.cancel(userId, sessionId);
	}

	@PatchMapping("/sessions/{sessionId}/team-name")
	public SessionResponse renameTeam(
			@AuthUserId Long userId,
			@PathVariable Long sessionId,
			@Valid @RequestBody RenameSessionTeamRequest request) {
		return sessionService.renameTeam(userId, sessionId, request.teamName());
	}
}
