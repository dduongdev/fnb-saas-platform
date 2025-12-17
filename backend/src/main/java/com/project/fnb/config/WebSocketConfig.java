package com.project.fnb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Client sẽ subscribe vào các topic bắt đầu bằng /topic
        config.enableSimpleBroker("/topic");
        
        // Client gửi message lên server qua prefix /app
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint để Frontend kết nối Socket
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Cho phép mọi domain (Dev)
                .withSockJS(); // Fallback nếu trình duyệt không hỗ trợ WS
    }
}