package com.scrim.lolscrim.domain.player;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RiotApiProperties.class)
public class RiotApiConfig {

	@Bean
	RiotApiClient riotApiClient(RiotApiProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()));
		requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));
		RestClient regionalClient = RestClient.builder()
				.baseUrl(properties.accountBaseUrl())
				.requestFactory(requestFactory)
				.build();
		RestClient platformClient = RestClient.builder()
				.baseUrl(properties.platformBaseUrl())
				.requestFactory(requestFactory)
				.build();
		return new RiotApiClient(regionalClient, platformClient, properties.apiKey());
	}
}
