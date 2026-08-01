package com.scrim.lolscrim.domain.player;

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

import com.scrim.lolscrim.domain.player.dto.AddRiotPlayerRequest;
import com.scrim.lolscrim.domain.player.dto.RiotPlayerResponse;
import com.scrim.lolscrim.domain.player.dto.RenameRiotPlayerRequest;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms/{roomId}/players")
@RequiredArgsConstructor
public class RiotPlayerController {

	private final RiotPlayerService riotPlayerService;

	@GetMapping
	public List<RiotPlayerResponse> getPlayers(
			@AuthUserId Long userId,
			@PathVariable Long roomId) {
		return riotPlayerService.getPlayers(userId, roomId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RiotPlayerResponse addPlayer(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@Valid @RequestBody AddRiotPlayerRequest request) {
		return riotPlayerService.addPlayer(userId, roomId, request);
	}

	@PostMapping("/sync")
	public List<RiotPlayerResponse> syncPlayers(
			@AuthUserId Long userId,
			@PathVariable Long roomId) {
		return riotPlayerService.syncPlayers(userId, roomId);
	}

	@PatchMapping("/{playerId}")
	public RiotPlayerResponse renamePlayer(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@PathVariable Long playerId,
			@Valid @RequestBody RenameRiotPlayerRequest request) {
		return riotPlayerService.renamePlayer(userId, roomId, playerId, request.displayName());
	}

	@DeleteMapping("/{playerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removePlayer(
			@AuthUserId Long userId,
			@PathVariable Long roomId,
			@PathVariable Long playerId) {
		riotPlayerService.removePlayer(userId, roomId, playerId);
	}
}
