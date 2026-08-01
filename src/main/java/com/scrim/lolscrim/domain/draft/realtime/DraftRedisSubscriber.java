package com.scrim.lolscrim.domain.draft.realtime;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftRedisSubscriber implements MessageListener {

	private final ObjectMapper objectMapper;
	private final DraftRealtimeHub realtimeHub;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			DraftRealtimeEvent event = objectMapper.readValue(
					new String(message.getBody(), StandardCharsets.UTF_8),
					DraftRealtimeEvent.class);
			realtimeHub.broadcast(event);
		} catch (Exception exception) {
			log.warn("Redis Draft 이벤트를 처리하지 못했습니다.", exception);
		}
	}
}
