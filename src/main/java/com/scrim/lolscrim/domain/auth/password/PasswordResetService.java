package com.scrim.lolscrim.domain.auth.password;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.auth.UserSessionRepository;
import com.scrim.lolscrim.domain.auth.password.dto.PasswordResetConfirmRequest;
import com.scrim.lolscrim.domain.auth.password.dto.PasswordResetRequest;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.domain.user.UserStatus;
import com.scrim.lolscrim.global.error.ApiException;
import com.scrim.lolscrim.global.security.SecureTokens;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

	private final UserRepository userRepository;
	private final UserSessionRepository userSessionRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordResetNotifier passwordResetNotifier;
	private final PasswordEncoder passwordEncoder;

	@Value("${app.password-reset-token-ttl-minutes}")
	private long tokenTtlMinutes;

	@Transactional
	public void requestReset(PasswordResetRequest request) {
		userRepository.findByEmail(request.email())
				.filter(user -> user.getStatus() == UserStatus.ACTIVE)
				.ifPresent(user -> issueResetToken(user, request.email()));
	}

	@Transactional
	public void confirmReset(PasswordResetConfirmRequest request) {
		LocalDateTime now = LocalDateTime.now();
		PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(SecureTokens.sha256Hex(request.resetToken()))
				.filter(token -> token.isUsable(now))
				.orElseThrow(() -> invalidTokenException());
		User user = userRepository.findById(resetToken.getUserId())
				.filter(found -> found.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> invalidTokenException());
		if (user.getPasswordHash() != null
				&& passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
		}

		user.changePassword(passwordEncoder.encode(request.newPassword()));
		passwordResetTokenRepository.markAllUsedByUserId(user.getId(), now);
		userSessionRepository.revokeAllByUserId(user.getId(), now);
	}

	private void issueResetToken(User user, String email) {
		LocalDateTime now = LocalDateTime.now();
		passwordResetTokenRepository.markAllUsedByUserId(user.getId(), now);
		String rawToken = SecureTokens.randomHex(32);
		passwordResetTokenRepository.save(PasswordResetToken.create(
				user.getId(),
				SecureTokens.sha256Hex(rawToken),
				now.plusMinutes(tokenTtlMinutes)));
		passwordResetNotifier.send(email, rawToken);
	}

	private static ApiException invalidTokenException() {
		return new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 재설정 토큰입니다.");
	}
}
