package com.scrim.lolscrim.domain.draft.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.scrim.lolscrim.domain.draft.DraftService;
import com.scrim.lolscrim.domain.draft.dto.ReadyDraftRequest;

import tools.jackson.databind.ObjectMapper;

class DraftWebSocketHandlerTest {

	private final DraftService draftService = mock(DraftService.class);
	private final DraftRealtimeQueryService queryService = mock(DraftRealtimeQueryService.class);
	private final DraftRealtimeHub realtimeHub = mock(DraftRealtimeHub.class);
	private final DraftWebSocketHandler handler = new DraftWebSocketHandler(
			new ObjectMapper(), draftService, queryService, realtimeHub);

	@Test
	void readyCommandUsesSameDraftServiceAsRest() throws Exception {
		WebSocketSession session = session();

		handler.handleTextMessage(
				session,
				new TextMessage("{\"type\":\"READY\",\"expectedVersion\":2}"));

		verify(draftService).ready(1L, 60L, new ReadyDraftRequest(2));
	}

	@Test
	void reconnectSendsReplayedEvents() throws Exception {
		WebSocketSession session = session();
		DraftRealtimeEvent replay = new DraftRealtimeEvent(
				60L, 3, 2, "HOVER_UPDATED", Map.of("championId", 266));
		when(queryService.eventsForConnection(1L, 60L, 2)).thenReturn(List.of(replay));

		handler.afterConnectionEstablished(session);

		verify(realtimeHub).register(60L, session);
		verify(realtimeHub).send(session, replay);
	}

	private WebSocketSession session() {
		WebSocketSession session = mock(WebSocketSession.class);
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(DraftWebSocketHandshakeInterceptor.USER_ID, 1L);
		attributes.put(DraftWebSocketHandshakeInterceptor.DRAFT_ID, 60L);
		attributes.put(DraftWebSocketHandshakeInterceptor.LAST_SEQ, 2);
		when(session.getAttributes()).thenReturn(attributes);
		return session;
	}
}
