package com.scrim.lolscrim.domain.match;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.match.dto.MatchOverviewResponse;
import com.scrim.lolscrim.domain.match.dto.MatchResponse;
import com.scrim.lolscrim.domain.match.dto.MatchStartRequestResponse;
import com.scrim.lolscrim.domain.match.dto.ProposeMatchResultRequest;
import com.scrim.lolscrim.domain.match.dto.RequestMatchStartRequest;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatchController {

	private final MatchService matchService;

	@GetMapping("/sessions/{sessionId}/matches")
	public MatchOverviewResponse getOverview(
			@AuthUserId Long userId,
			@PathVariable Long sessionId) {
		return matchService.getOverview(userId, sessionId);
	}

	@PostMapping("/sessions/{sessionId}/match-start-requests")
	public MatchStartRequestResponse requestStart(
			@AuthUserId Long userId,
			@PathVariable Long sessionId,
			@Valid @RequestBody RequestMatchStartRequest request) {
		return matchService.requestStart(userId, sessionId, request.blueTeamSide());
	}

	@PostMapping("/match-start-requests/{requestId}/accept")
	public MatchResponse acceptStart(
			@AuthUserId Long userId,
			@PathVariable Long requestId) {
		return matchService.acceptStart(userId, requestId);
	}

	@PostMapping("/match-start-requests/{requestId}/reject")
	public MatchStartRequestResponse rejectStart(
			@AuthUserId Long userId,
			@PathVariable Long requestId) {
		return matchService.rejectStart(userId, requestId);
	}

	@PostMapping("/match-start-requests/{requestId}/cancel")
	public MatchStartRequestResponse cancelStart(
			@AuthUserId Long userId,
			@PathVariable Long requestId) {
		return matchService.cancelStart(userId, requestId);
	}

	@PostMapping("/matches/{matchId}/start")
	public MatchResponse startLive(
			@AuthUserId Long userId,
			@PathVariable Long matchId) {
		return matchService.startLive(userId, matchId);
	}

	@PostMapping("/matches/{matchId}/results")
	public MatchResponse proposeResult(
			@AuthUserId Long userId,
			@PathVariable Long matchId,
			@Valid @RequestBody ProposeMatchResultRequest request) {
		return matchService.proposeResult(userId, matchId, request);
	}

	@PostMapping("/matches/{matchId}/results/accept")
	public MatchResponse acceptResult(
			@AuthUserId Long userId,
			@PathVariable Long matchId) {
		return matchService.acceptResult(userId, matchId);
	}

	@PostMapping("/matches/{matchId}/results/reject")
	public MatchResponse rejectResult(
			@AuthUserId Long userId,
			@PathVariable Long matchId) {
		return matchService.rejectResult(userId, matchId);
	}

	@PostMapping("/sessions/{sessionId}/finish")
	public MatchOverviewResponse finishUnlimited(
			@AuthUserId Long userId,
			@PathVariable Long sessionId) {
		return matchService.finishUnlimited(userId, sessionId);
	}
}
