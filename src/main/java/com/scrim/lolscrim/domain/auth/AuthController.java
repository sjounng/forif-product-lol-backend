package com.scrim.lolscrim.domain.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
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
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final String REFRESH_COOKIE = "scrim_refresh_token";

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
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		AuthResponse response = authService.login(request, userAgent, httpRequest.getRemoteAddr());
		setRefreshCookie(httpResponse, response.refreshToken(), httpRequest.isSecure());
		return response;
	}

	@PostMapping("/refresh")
	public AuthResponse refresh(
			@RequestBody(required = false) RefreshRequest request,
			@CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
			@RequestHeader(value = "User-Agent", required = false) String userAgent,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String refreshToken = resolveRefreshToken(request, cookieToken);
		AuthResponse response = authService.refresh(
				refreshToken,
				userAgent,
				httpRequest.getRemoteAddr());
		setRefreshCookie(httpResponse, response.refreshToken(), httpRequest.isSecure());
		return response;
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(
			@RequestBody(required = false) RefreshRequest request,
			@CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String refreshToken = resolveRefreshToken(request, cookieToken);
		authService.logout(refreshToken);
		httpResponse.addHeader(
				HttpHeaders.SET_COOKIE,
				ResponseCookie.from(REFRESH_COOKIE, "")
						.httpOnly(true)
						.secure(httpRequest.isSecure())
						.sameSite("Lax")
						.path("/")
						.maxAge(Duration.ZERO)
						.build()
						.toString());
	}

	@GetMapping("/me")
	public UserResponse me(@AuthUserId Long userId) {
		return authService.me(userId);
	}

	private static String resolveRefreshToken(RefreshRequest request, String cookieToken) {
		String bodyToken = request == null ? null : request.refreshToken();
		String token = bodyToken == null || bodyToken.isBlank() ? cookieToken : bodyToken;
		if (token == null || token.isBlank()) {
			throw new ApiException(ErrorCode.AUTH_REQUIRED, "리프레시 토큰이 필요합니다.");
		}
		return token;
	}

	private static void setRefreshCookie(
			HttpServletResponse response,
			String refreshToken,
			boolean secure) {
		response.addHeader(
				HttpHeaders.SET_COOKIE,
				ResponseCookie.from(REFRESH_COOKIE, refreshToken)
						.httpOnly(true)
						.secure(secure)
						.sameSite("Lax")
						.path("/")
						.maxAge(Duration.ofDays(14))
						.build()
						.toString());
	}
}
