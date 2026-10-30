package com.jhwjhw.naverworkscloneapi.presence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// Redis 방송 채널을 듣는 장치. 서버가 켜지면 realtime:presence 채널 구독을 시작함(배포용).
@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisPresenceConfig {

    @Bean
    RedisMessageListenerContainer presenceListenerContainer(RedisConnectionFactory connectionFactory,
            RedisPresenceBroadcaster broadcaster) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(broadcaster, new ChannelTopic(RedisPresenceBroadcaster.CHANNEL));
        return container;
    }
}
