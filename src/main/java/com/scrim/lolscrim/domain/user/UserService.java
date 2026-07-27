package com.scrim.lolscrim.domain.user;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.auth.UserSessionRepository;
import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.domain.user.dto.ChangePasswordRequest;
import com.scrim.lolscrim.domain.user.dto.UpdateProfileRequest;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final UserSessionRepository userSessionRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
		user.updateProfile(request.displayName(), request.avatarUrl());
		return UserResponse.from(user);
	}

	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
		}
		if (user.getPasswordHash() == null
				|| !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
		}

		user.changePassword(passwordEncoder.encode(request.newPassword()));
		userSessionRepository.revokeAllByUserId(userId, LocalDateTime.now());
	}
}
