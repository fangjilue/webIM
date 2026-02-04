package com.webim.config;

import com.webim.service.RedisMessageListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis 配置类
 * 配置消息监听容器，绑定监听器与 Topic
 */
@Configuration
public class RedisConfig {

    public static final String IM_TOPIC = "webim-chat-topic";

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 订阅所有 webim-chat-topic 的消息
        container.addMessageListener(listenerAdapter, new PatternTopic(IM_TOPIC));
        return container;
    }

    /**
     * 绑定自定义的监听器
     */
    @Bean
    MessageListenerAdapter listenerAdapter(RedisMessageListener listener) {
        // MessageListenerAdapter 会自动调用 listener.onMessage
        return new MessageListenerAdapter(listener, "onMessage");
    }
}
