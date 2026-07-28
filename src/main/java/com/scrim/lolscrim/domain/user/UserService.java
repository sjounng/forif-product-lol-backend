package com.scrim.lolscrim.domain.user;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.auth.UserSessionRepository;
import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.domain.user.dto.ChangePasswordRequest;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final int DISPLAY_NAME_MAX_LENGTH = 50;
	private static final int AVATAR_URL_MAX_LENGTH = 255;

	private final UserRepository userRepository;
	private final UserSessionRepository userSessionRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 진짜 부분 수정(PATCH)이다 — 요청에 없는 필드는 그대로 둔다.
	 * 필드가 명시적으로 null이면(예: avatarUrl) 그 값을 지운다.
	 */
	@Transactional
	public UserResponse updateProfile(Long userId, JsonNode request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
		}

		String displayName = user.getDisplayName();
		if (request.has("displayName")) {
			JsonNode node = request.get("displayName");
			String value = node.isNull() ? null : node.asString();
			if (value == null || value.isBlank()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "displayName: 공백일 수 없습니다.");
			}
			if (value.length() > DISPLAY_NAME_MAX_LENGTH) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"displayName: " + DISPLAY_NAME_MAX_LENGTH + "자 이하여야 합니다.");
			}
			displayName = value;
		}

		String avatarUrl = user.getAvatarUrl();
		if (request.has("avatarUrl")) {
			JsonNode node = request.get("avatarUrl");
			if (node.isNull()) {
				avatarUrl = null;
			} else {
				String value = node.asString();
				if (value.length() > AVATAR_URL_MAX_LENGTH) {
					throw new ApiException(HttpStatus.BAD_REQUEST,
							"avatarUrl: " + AVATAR_URL_MAX_LENGTH + "자 이하여야 합니다.");
				}
				if (!isHttpUrl(value)) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "avatarUrl: 올바른 http(s) URL 형식이 아닙니다.");
				}
				avatarUrl = value;
			}
		}

		user.updateProfile(displayName, avatarUrl);
		return UserResponse.from(user);
	}

	private static boolean isHttpUrl(String value) {
		try {
			URI uri = new URI(value);
			return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					&& uri.getHost() != null;
		} catch (URISyntaxException e) {
			return false;
		}
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
