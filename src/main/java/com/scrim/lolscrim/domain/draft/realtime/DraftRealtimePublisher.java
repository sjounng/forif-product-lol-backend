package com.scrim.lolscrim.domain.draft.realtime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftRealtimePublisher {

	public static final String CHANNEL = "lol-scrim:draft-events";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final DraftRealtimeHub realtimeHub;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(DraftEventCommitted committed) {
		DraftRealtimeEvent event = committed.toRealtimeEvent();
		try {
			String message = objectMapper.writeValueAsString(event);
			Long subscribers = redisTemplate.convertAndSend(CHANNEL, message);
			if (subscribers == null || subscribers == 0) {
				realtimeHub.broadcast(event);
			}
		} catch (RuntimeException exception) {
			log.warn("Redis Draft 이벤트 발행 실패. 로컬 연결로 전달합니다. draftId={}", event.draftId(), exception);
			realtimeHub.broadcast(event);
		}
	}
}
