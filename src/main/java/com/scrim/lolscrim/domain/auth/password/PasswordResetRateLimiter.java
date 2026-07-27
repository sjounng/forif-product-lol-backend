package com.scrim.lolscrim.domain.auth.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PasswordResetRateLimiter {

	private static final RedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
				redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			return count
			""", Long.class);

	private final StringRedisTemplate redisTemplate;

	@Value("${app.password-reset-rate-limit.email-limit}")
	private long emailLimit;

	@Value("${app.password-reset-rate-limit.email-window-seconds}")
	private long emailWindowSeconds;

	@Value("${app.password-reset-rate-limit.ip-limit}")
	private long ipLimit;

	@Value("${app.password-reset-rate-limit.ip-window-seconds}")
	private long ipWindowSeconds;

	public void check(String email, String remoteAddr) {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		checkBucket("password-reset:email:" + sha256Hex(normalizedEmail), emailLimit, emailWindowSeconds);
		checkBucket("password-reset:ip:" + sha256Hex(normalizeIp(remoteAddr)), ipLimit, ipWindowSeconds);
	}

	private void checkBucket(String key, long limit, long windowSeconds) {
		try {
			Long count = redisTemplate.execute(
					INCREMENT_SCRIPT,
					List.of(key),
					String.valueOf(windowSeconds));
			if (count == null) {
				throw unavailableException();
			}
			if (count > limit) {
				throw new ApiException(
						HttpStatus.TOO_MANY_REQUESTS,
						"비밀번호 재설정 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
			}
		} catch (DataAccessException e) {
			throw unavailableException();
		}
	}

	private static String normalizeIp(String remoteAddr) {
		return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static ApiException unavailableException() {
		return new ApiException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"비밀번호 재설정 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
	}
}
