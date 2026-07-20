package com.scrim.lolscrim.domain.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.auth.dto.AuthResponse;
import com.scrim.lolscrim.domain.auth.dto.LoginRequest;
import com.scrim.lolscrim.domain.auth.dto.RefreshRequest;
import com.scrim.lolscrim.domain.auth.dto.SignupRequest;
import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.global.auth.AuthUserId;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse signup(@Valid @RequestBody SignupRequest request) {
		return authService.signup(request);
	}

	@PostMapping("/login")
	public AuthResponse login(
			@Valid @RequestBody LoginRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent,
			HttpServletRequest httpRequest) {
		return authService.login(request, userAgent, httpRequest.getRemoteAddr());
	}

	@PostMapping("/refresh")
	public AuthResponse refresh(
			@Valid @RequestBody RefreshRequest request,
			@RequestHeader(value = "User-Agent", required = false) String userAgent,
			HttpServletRequest httpRequest) {
		return authService.refresh(request.refreshToken(), userAgent, httpRequest.getRemoteAddr());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshRequest request) {
		authService.logout(request.refreshToken());
	}

	@GetMapping("/me")
	public UserResponse me(@AuthUserId Long userId) {
		return authService.me(userId);
	}
}
