package com.scrim.lolscrim.domain.draft.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DraftRealtimeHub {

	private final ObjectMapper objectMapper;
	private final Map<Long, Map<String, WebSocketSession>> sessionsByDraft = new ConcurrentHashMap<>();

	public void register(Long draftId, WebSocketSession session) {
		sessionsByDraft.computeIfAbsent(draftId, ignored -> new ConcurrentHashMap<>())
				.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
	}

	public void unregister(Long draftId, String sessionId) {
		Map<String, WebSocketSession> sessions = sessionsByDraft.get(draftId);
		if (sessions == null) {
			return;
		}
		sessions.remove(sessionId);
		if (sessions.isEmpty()) {
			sessionsByDraft.remove(draftId, sessions);
		}
	}

	public void broadcast(DraftRealtimeEvent event) {
		Map<String, WebSocketSession> sessions = sessionsByDraft.get(event.draftId());
		if (sessions == null || sessions.isEmpty()) {
			return;
		}
		String json = serialize(event);
		sessions.forEach((sessionId, session) -> {
			if (!session.isOpen()) {
				unregister(event.draftId(), sessionId);
				return;
			}
			try {
				session.sendMessage(new TextMessage(json));
			} catch (IOException exception) {
				unregister(event.draftId(), sessionId);
			}
		});
	}

	public void send(WebSocketSession session, DraftRealtimeEvent event) throws IOException {
		WebSocketSession target = sessionsByDraft
				.getOrDefault(event.draftId(), Map.of())
				.getOrDefault(session.getId(), session);
		synchronized (target) {
			target.sendMessage(new TextMessage(serialize(event)));
		}
	}

	private String serialize(DraftRealtimeEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Draft 실시간 이벤트 직렬화에 실패했습니다.", exception);
		}
	}
}
