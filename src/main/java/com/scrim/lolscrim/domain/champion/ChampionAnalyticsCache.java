package com.scrim.lolscrim.domain.champion;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.scrim.lolscrim.domain.champion.dto.ChampionAnalyticsResponse;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class ChampionAnalyticsCache {

	private static final String CACHE_KEY = "lol-scrim:champion-analytics:v1";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final Duration ttl;
	private volatile LocalEntry localEntry;

	public ChampionAnalyticsCache(
			StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper,
			@Value("${app.champion-analytics.cache-ttl:PT5M}") Duration ttl) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.ttl = ttl;
	}

	public Optional<ChampionAnalyticsResponse> get() {
		LocalEntry current = localEntry;
		if (current != null && current.expiresAt().isAfter(Instant.now())) {
			return Optional.of(current.response());
		}

		try {
			String payload = redisTemplate.opsForValue().get(CACHE_KEY);
			if (payload == null) {
				return Optional.empty();
			}
			ChampionAnalyticsResponse response =
					objectMapper.readValue(payload, ChampionAnalyticsResponse.class);
			Long remainingMillis = redisTemplate.getExpire(CACHE_KEY, TimeUnit.MILLISECONDS);
			Duration localTtl = remainingMillis != null && remainingMillis > 0
					? Duration.ofMillis(remainingMillis)
					: ttl;
			localEntry = new LocalEntry(response, Instant.now().plus(localTtl));
			return Optional.of(response);
		} catch (RuntimeException exception) {
			log.warn("챔피언 통계 Redis 캐시 조회에 실패해 로컬 캐시만 사용합니다.", exception);
			return Optional.empty();
		}
	}

	public void put(ChampionAnalyticsResponse response) {
		localEntry = new LocalEntry(response, Instant.now().plus(ttl));
		try {
			redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(response), ttl);
		} catch (RuntimeException exception) {
			log.warn("챔피언 통계 Redis 캐시 저장에 실패해 로컬 캐시만 사용합니다.", exception);
		}
	}

	private record LocalEntry(
			ChampionAnalyticsResponse response,
			Instant expiresAt) {
	}
}
