package com.scrim.lolscrim.domain.draft.realtime;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.scrim.lolscrim.domain.draft.DraftService;
import com.scrim.lolscrim.domain.draft.dto.AssignChampionRequest;
import com.scrim.lolscrim.domain.draft.dto.ConfirmAssignmentRequest;
import com.scrim.lolscrim.domain.draft.dto.HoverDraftRequest;
import com.scrim.lolscrim.domain.draft.dto.LockDraftRequest;
import com.scrim.lolscrim.domain.draft.dto.ReadyDraftRequest;
import com.scrim.lolscrim.global.error.ApiException;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class DraftWebSocketHandler extends TextWebSocketHandler {

	private final ObjectMapper objectMapper;
	private final DraftService draftService;
	private final DraftRealtimeQueryService queryService;
	private final DraftRealtimeHub realtimeHub;

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		Long draftId = attribute(session, DraftWebSocketHandshakeInterceptor.DRAFT_ID, Long.class);
		Long userId = attribute(session, DraftWebSocketHandshakeInterceptor.USER_ID, Long.class);
		Integer lastSeq = attribute(session, DraftWebSocketHandshakeInterceptor.LAST_SEQ, Integer.class);
		try {
			realtimeHub.register(draftId, session);
			for (DraftRealtimeEvent event : queryService.eventsForConnection(userId, draftId, lastSeq)) {
				realtimeHub.send(session, event);
			}
		} catch (RuntimeException exception) {
			realtimeHub.unregister(draftId, session.getId());
			session.close(CloseStatus.POLICY_VIOLATION);
		}
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		Long draftId = attribute(session, DraftWebSocketHandshakeInterceptor.DRAFT_ID, Long.class);
		Long userId = attribute(session, DraftWebSocketHandshakeInterceptor.USER_ID, Long.class);
		try {
			JsonNode command = objectMapper.readTree(message.getPayload());
			String type = requiredText(command, "type");
			int expectedVersion = requiredInt(command, "expectedVersion");
			switch (type) {
				case "READY" -> draftService.ready(
						userId, draftId, new ReadyDraftRequest(expectedVersion));
				case "HOVER" -> draftService.hover(
						userId,
						draftId,
						new HoverDraftRequest(
							requiredInt(command, "stepNo"),
							nullableInt(command, "championId"),
							expectedVersion));
				case "LOCK" -> draftService.lock(
						userId,
						draftId,
						new LockDraftRequest(
							requiredInt(command, "stepNo"),
							requiredInt(command, "championId"),
							nullableLong(command, "playerId"),
							expectedVersion));
				case "ASSIGN_CHAMPION", "SWAP_CHAMPIONS" -> draftService.assign(
						userId,
						draftId,
						new AssignChampionRequest(
							requiredLong(command, "playerId"),
							requiredInt(command, "championId"),
							expectedVersion));
				case "CONFIRM_ASSIGNMENT" -> draftService.confirmAssignment(
						userId, draftId, new ConfirmAssignmentRequest(expectedVersion));
				default -> throw new IllegalArgumentException("지원하지 않는 Draft 명령입니다.");
			}
		} catch (ApiException exception) {
			sendError(session, draftId, exception.getCode().name(), exception.getMessage());
		} catch (RuntimeException exception) {
			sendError(session, draftId, "VALIDATION_ERROR", "Draft 명령 형식이 올바르지 않습니다.");
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		Long draftId = (Long) session.getAttributes().get(DraftWebSocketHandshakeInterceptor.DRAFT_ID);
		if (draftId != null) {
			realtimeHub.unregister(draftId, session.getId());
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		session.close(CloseStatus.SERVER_ERROR);
	}

	private void sendError(WebSocketSession session, Long draftId, String code, String message) throws IOException {
		realtimeHub.send(session, new DraftRealtimeEvent(
				draftId,
				-1,
				-1,
				"ERROR",
				Map.of("code", code, "message", message)));
	}

	private String requiredText(JsonNode node, String field) {
		String value = node.path(field).asText("");
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + "가 필요합니다.");
		}
		return value;
	}

	private int requiredInt(JsonNode node, String field) {
		if (!node.has(field) || !node.get(field).canConvertToInt()) {
			throw new IllegalArgumentException(field + "가 필요합니다.");
		}
		return node.get(field).intValue();
	}

	private long requiredLong(JsonNode node, String field) {
		if (!node.has(field) || !node.get(field).canConvertToLong()) {
			throw new IllegalArgumentException(field + "가 필요합니다.");
		}
		return node.get(field).longValue();
	}

	private Integer nullableInt(JsonNode node, String field) {
		return !node.has(field) || node.get(field).isNull() ? null : requiredInt(node, field);
	}

	private Long nullableLong(JsonNode node, String field) {
		return !node.has(field) || node.get(field).isNull() ? null : requiredLong(node, field);
	}

	private <T> T attribute(WebSocketSession session, String name, Class<T> type) {
		Object value = session.getAttributes().get(name);
		if (!type.isInstance(value)) {
			throw new IllegalStateException("WebSocket 인증 정보가 없습니다.");
		}
		return type.cast(value);
	}
}
