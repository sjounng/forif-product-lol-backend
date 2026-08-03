package com.scrim.lolscrim.domain.champion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.data-dragon")
public record DataDragonProperties(
		String baseUrl,
		String koreanLocale,
		String englishLocale,
		boolean syncOnStartup,
		int connectTimeoutMillis,
		int readTimeoutMillis) {
}
