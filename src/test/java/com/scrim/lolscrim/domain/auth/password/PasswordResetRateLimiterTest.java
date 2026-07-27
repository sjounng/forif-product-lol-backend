package com.scrim.lolscrim.domain.auth.password;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import com.scrim.lolscrim.global.error.ApiException;

@ExtendWith(MockitoExtension.class)
class PasswordResetRateLimiterTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	private PasswordResetRateLimiter rateLimiter;

	@BeforeEach
	void setUp() {
		rateLimiter = new PasswordResetRateLimiter(redisTemplate);
		ReflectionTestUtils.setField(rateLimiter, "emailLimit", 3L);
		ReflectionTestUtils.setField(rateLimiter, "emailWindowSeconds", 300L);
		ReflectionTestUtils.setField(rateLimiter, "ipLimit", 20L);
		ReflectionTestUtils.setField(rateLimiter, "ipWindowSeconds", 3600L);
	}

	@Test
	@SuppressWarnings("unchecked")
	void allowsRequestWithinEmailAndIpLimits() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
				.thenReturn(1L, 1L);

		rateLimiter.check("User@Example.com", "127.0.0.1");
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsRequestOverEmailLimit() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
				.thenReturn(4L);

		assertThatThrownBy(() -> rateLimiter.check("user@example.com", "127.0.0.1"))
				.isInstanceOf(ApiException.class)
				.hasMessage("비밀번호 재설정 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsRequestOverIpLimit() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
				.thenReturn(1L, 21L);

		assertThatThrownBy(() -> rateLimiter.check("user@example.com", "127.0.0.1"))
				.isInstanceOf(ApiException.class)
				.hasMessage("비밀번호 재설정 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
	}
}
