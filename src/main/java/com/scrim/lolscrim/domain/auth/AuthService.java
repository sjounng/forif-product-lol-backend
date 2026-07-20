package com.scrim.lolscrim.domain.auth;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scrim.lolscrim.domain.auth.dto.AuthResponse;
import com.scrim.lolscrim.domain.auth.dto.LoginRequest;
import com.scrim.lolscrim.domain.auth.dto.SignupRequest;
import com.scrim.lolscrim.domain.auth.dto.UserResponse;
import com.scrim.lolscrim.domain.user.User;
import com.scrim.lolscrim.domain.user.UserRepository;
import com.scrim.lolscrim.domain.user.UserStatus;
import com.scrim.lolscrim.global.auth.JwtProvider;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final UserSessionRepository userSessionRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;

	@Value("${app.refresh-token-ttl-days}")
	private long refreshTokenTtlDays;

	@Transactional
	public UserResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
		}
		User user = User.create(
				request.email(),
				passwordEncoder.encode(request.password()),
				request.displayName());
		return UserResponse.from(userRepository.save(user));
	}

	@Transactional
	public AuthResponse login(LoginRequest request, String userAgent, String remoteAddr) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));
		if (user.getPasswordHash() == null
				|| !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
		}
		LocalDateTime now = LocalDateTime.now();
		user.markLoggedIn(now);
		return issueTokens(user, userAgent, remoteAddr, now);
	}

	@Transactional
	public AuthResponse refresh(String refreshToken, String userAgent, String remoteAddr) {
		LocalDateTime now = LocalDateTime.now();
		UserSession session = userSessionRepository.findByRefreshTokenHash(sha256Hex(refreshToken))
				.filter(s -> s.isUsable(now))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."));
		User user = userRepository.findById(session.getUserId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
		}
		// 토큰 로테이션: 기존 세션 폐기 후 새 토큰 발급
		session.revoke(now);
		return issueTokens(user, userAgent, remoteAddr, now);
	}

	@Transactional
	public void logout(String refreshToken) {
		LocalDateTime now = LocalDateTime.now();
		userSessionRepository.findByRefreshTokenHash(sha256Hex(refreshToken))
				.filter(s -> s.getRevokedAt() == null)
				.ifPresent(s -> s.revoke(now));
	}

	@Transactional(readOnly = true)
	public UserResponse me(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
		return UserResponse.from(user);
	}

	private AuthResponse issueTokens(User user, String userAgent, String remoteAddr, LocalDateTime now) {
		String refreshToken = generateRefreshToken();
		userSessionRepository.save(UserSession.create(
				user.getId(),
				sha256Hex(refreshToken),
				truncate(userAgent, 255),
				toIpBytes(remoteAddr),
				now.plusDays(refreshTokenTtlDays)));
		String accessToken = jwtProvider.createAccessToken(user.getId());
		return AuthResponse.of(accessToken, jwtProvider.getAccessTokenTtlSeconds(), refreshToken,
				UserResponse.from(user));
	}

	private static String generateRefreshToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] toIpBytes(String remoteAddr) {
		if (remoteAddr == null || remoteAddr.isBlank()) {
			return null;
		}
		try {
			return InetAddress.getByName(remoteAddr).getAddress();
		} catch (Exception e) {
			return null;
		}
	}

	private static String truncate(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
