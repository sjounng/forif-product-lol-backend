package com.scrim.lolscrim.domain.user;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.domain.user.dto.ChangePasswordRequest;
import com.scrim.lolscrim.domain.user.dto.UserSearchResponse;
import com.scrim.lolscrim.domain.user.dto.UserProfileResponse;
import com.scrim.lolscrim.domain.player.dto.AddRiotPlayerRequest;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/search")
	public List<UserSearchResponse> search(
			@AuthUserId Long userId,
			@RequestParam(name = "q", defaultValue = "") String query) {
		return userService.searchActiveUsers(userId, query);
	}

	@PatchMapping("/me")
	public UserResponse updateMe(@AuthUserId Long userId, @RequestBody JsonNode request) {
		return userService.updateProfile(userId, request);
	}

	@GetMapping("/me/profile")
	public UserProfileResponse getProfile(@AuthUserId Long userId) {
		return userService.getProfile(userId);
	}

	@PutMapping("/me/riot-account")
	public UserProfileResponse linkRiotAccount(
			@AuthUserId Long userId,
			@Valid @RequestBody AddRiotPlayerRequest request) {
		return userService.linkRiotAccount(userId, request);
	}

	@PatchMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(
			@AuthUserId Long userId,
			@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(userId, request);
	}
}
