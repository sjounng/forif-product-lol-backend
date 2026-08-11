package com.scrim.lolscrim.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.scrim.lolscrim.domain.draft.realtime.DraftWebSocketHandler;
import com.scrim.lolscrim.domain.draft.realtime.DraftWebSocketHandshakeInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class DraftWebSocketConfig implements WebSocketConfigurer {

	private final DraftWebSocketHandler handler;
	private final DraftWebSocketHandshakeInterceptor handshakeInterceptor;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/ws/drafts/{draftId}")
				.addInterceptors(handshakeInterceptor)
				.setAllowedOrigins("http://localhost:3000", "http://localhost:3001");
	}
}
