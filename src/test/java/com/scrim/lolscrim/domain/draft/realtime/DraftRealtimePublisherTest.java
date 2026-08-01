package com.scrim.lolscrim.domain.draft.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.ObjectMapper;

class DraftRealtimePublisherTest {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	private final DraftRealtimeHub realtimeHub = mock(DraftRealtimeHub.class);
	private final DraftRealtimePublisher publisher = new DraftRealtimePublisher(
			redisTemplate, new ObjectMapper(), realtimeHub);

	@Test
	void noRedisSubscriberFallsBackToLocalWebSockets() {
		DraftEventCommitted committed = new DraftEventCommitted(
				60L, 3, 2, "HOVER_UPDATED", Map.of("championId", 266));
		when(redisTemplate.convertAndSend(
				DraftRealtimePublisher.CHANNEL,
				"{\"draftId\":60,\"seq\":3,\"version\":2,\"type\":\"HOVER_UPDATED\",\"payload\":{\"championId\":266}}"))
				.thenReturn(0L);

		publisher.publish(committed);

		verify(realtimeHub).broadcast(committed.toRealtimeEvent());
	}
}
