package com.scrim.lolscrim.domain.auth.password;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.scrim.lolscrim.domain.auth.password.dto.PasswordResetConfirmRequest;
import com.scrim.lolscrim.domain.auth.password.dto.PasswordResetRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

	private final PasswordResetService passwordResetService;
	private final PasswordResetRateLimiter passwordResetRateLimiter;

	@PostMapping("/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void requestReset(
			@Valid @RequestBody PasswordResetRequest request,
			jakarta.servlet.http.HttpServletRequest httpRequest) {
		passwordResetRateLimiter.check(request.email(), httpRequest.getRemoteAddr());
		passwordResetService.requestReset(request);
	}

	@PostMapping("/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
		passwordResetService.confirmReset(request);
	}
}
