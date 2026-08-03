package com.scrim.lolscrim.domain.draft.realtime;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.scrim.lolscrim.global.auth.JwtProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DraftWebSocketHandshakeInterceptor implements HandshakeInterceptor {

	public static final String USER_ID = "draftSocketUserId";
	public static final String DRAFT_ID = "draftSocketDraftId";
	public static final String LAST_SEQ = "draftSocketLastSeq";

	private final JwtProvider jwtProvider;

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		try {
			URI uri = request.getURI();
			MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
			String accessToken = query.getFirst("accessToken");
			if (accessToken == null || accessToken.isBlank()) {
				response.setStatusCode(HttpStatus.UNAUTHORIZED);
				return false;
			}
			Long userId = jwtProvider.parseUserId(accessToken);
			Long draftId = parseDraftId(uri.getPath());
			Integer lastSeq = parseLastSeq(query.getFirst("lastSeq"));
			attributes.put(USER_ID, userId);
			attributes.put(DRAFT_ID, draftId);
			attributes.put(LAST_SEQ, lastSeq);
			return true;
		} catch (RuntimeException exception) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception) {
	}

	private Long parseDraftId(String path) {
		String[] segments = path.split("/");
		if (segments.length == 0) {
			throw new IllegalArgumentException("Draft ID가 없습니다.");
		}
		return Long.valueOf(segments[segments.length - 1]);
	}

	private Integer parseLastSeq(String value) {
		if (value == null || value.isBlank()) {
			return -1;
		}
		return Integer.valueOf(value);
	}
}
