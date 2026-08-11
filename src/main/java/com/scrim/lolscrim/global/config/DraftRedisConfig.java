package com.scrim.lolscrim.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.scrim.lolscrim.domain.draft.realtime.DraftRealtimePublisher;
import com.scrim.lolscrim.domain.draft.realtime.DraftRedisSubscriber;

@Configuration
public class DraftRedisConfig {

	@Bean
	RedisMessageListenerContainer draftRedisListenerContainer(
			RedisConnectionFactory connectionFactory,
			DraftRedisSubscriber subscriber) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(subscriber, new ChannelTopic(DraftRealtimePublisher.CHANNEL));
		container.setRecoveryInterval(5_000);
		return container;
	}
}
