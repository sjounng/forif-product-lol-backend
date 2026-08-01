package com.scrim.lolscrim.domain.player;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.riot-api")
public record RiotApiProperties(
		String apiKey,
		String accountBaseUrl,
		String platformBaseUrl,
		int connectTimeoutMillis,
		int readTimeoutMillis) {
}
