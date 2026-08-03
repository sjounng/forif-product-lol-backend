package com.scrim.lolscrim.domain.player;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.player.dto.AddPlayerRequest;
import com.scrim.lolscrim.domain.player.dto.PlayerResponse;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms/{roomId}/players")
@RequiredArgsConstructor
public class PlayerController {

	private final PlayerService playerService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PlayerResponse addPlayer(
			@AuthUserId Long userId, @PathVariable Long roomId, @Valid @RequestBody AddPlayerRequest request) {
		return playerService.addPlayer(userId, roomId, request);
	}

	@GetMapping
	public List<PlayerResponse> listPlayers(@AuthUserId Long userId, @PathVariable Long roomId) {
		return playerService.listPlayers(userId, roomId);
	}
}
