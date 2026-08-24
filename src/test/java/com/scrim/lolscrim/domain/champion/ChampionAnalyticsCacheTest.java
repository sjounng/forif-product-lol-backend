package com.scrim.lolscrim.domain.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.scrim.lolscrim.domain.champion.dto.ChampionAnalyticsResponse;

import tools.jackson.databind.ObjectMapper;

class ChampionAnalyticsCacheTest {

	private static final String CACHE_KEY = "lol-scrim:champion-analytics:v1";

	@Test
	void readsRedisOnceAndReusesTheLocalTtlCache() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		ObjectMapper objectMapper = new ObjectMapper();
		ChampionAnalyticsResponse expected = new ChampionAnalyticsResponse(3L, List.of());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(CACHE_KEY)).thenReturn(objectMapper.writeValueAsString(expected));
		when(redisTemplate.getExpire(CACHE_KEY, TimeUnit.MILLISECONDS)).thenReturn(60_000L);
		ChampionAnalyticsCache cache =
				new ChampionAnalyticsCache(redisTemplate, objectMapper, Duration.ofMinutes(5));

		assertThat(cache.get()).contains(expected);
		assertThat(cache.get()).contains(expected);

		verify(valueOperations).get(CACHE_KEY);
	}

	@Test
	void writesRedisWithTtlAndMakesTheValueImmediatelyAvailableLocally() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		ObjectMapper objectMapper = new ObjectMapper();
		Duration ttl = Duration.ofMinutes(5);
		ChampionAnalyticsResponse response = new ChampionAnalyticsResponse(4L, List.of());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		ChampionAnalyticsCache cache = new ChampionAnalyticsCache(redisTemplate, objectMapper, ttl);

		cache.put(response);

		assertThat(cache.get()).contains(response);
		verify(valueOperations).set(eq(CACHE_KEY), any(String.class), eq(ttl));
		verify(valueOperations, never()).get(CACHE_KEY);
	}
}
