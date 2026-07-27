package com.scrim.lolscrim.domain.user;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.domain.user.dto.UpdateProfileRequest;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PatchMapping("/me")
	public UserResponse updateMe(@AuthUserId Long userId, @Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(userId, request);
	}
}
