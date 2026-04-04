package com.project.fnb.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình WebSocket với STOMP (Simple Text Oriented Messaging Protocol) 
 * cho Kitchen Display System (KDS).
 * 
 * <p><b>Cấu trúc:</b></p>
 * <ul>
 *   <li><b>STOMP Endpoint:</b> /ws/kds (điểm kết nối WebSocket)</li>
 *   <li><b>Application Destination Prefix:</b> /app (prefix cho client → server messages)</li>
 *   <li><b>Broker Destination Prefix:</b>/topic (prefix cho server → client broadcasts)</li>
 *   <li><b>Fallback:</b> SockJS (cho trình duyệt không hỗ trợ WebSocket)</li>
 * </ul>
 * 
 * <p><b>Message Flow:</b></p>
 * <pre>
 * Client Subscribe:
 *   SUBSCRIBE /topic/kds/{tenantId}/{kitchenAreaId}
 *   ↓ (Spring routes to KdsWebSocketController)
 *   handleSubscribe() receives subscription
 *   ↓
 *   publishes initial data
 *   ↓ (Server broadcasts via messagingTemplate.convertAndSend)
 *   SEND /topic/kds/{tenantId}/{kitchenAreaId}
 *   ↓
 *   Client receives message
 * </pre>
 * 
 * <p><b>Cross-Origin (CORS):</b> Configured để client từ domain khác có thể kết nối.</p>
 * 
 * @author FNB Team
 * @version 1.0
 */
@Configuration
@EnableWebSocketMessageBroker
public class KdsWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    /**
     * Cấu hình STOMP endpoint và SockJS fallback.
     * 
     * <p><b>Endpoint:</b> /ws/kds
     * <p><b>SockJS:</b> Cho phép client không hỗ trợ WebSocket (fallback to HTTP polling)</p>
     * <p><b>CORS:</b> Cho phép connections từ mọi origin.</p>
     * 
     * @param registry StompEndpointRegistry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/kds")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
    
    /**
     * Cấu hình message broker cho WebSocket.
     * 
     * <p><b>Broker Destination Prefixes:</b> /topic (for broadcasts)</p>
     * <p><b>Application Destination Prefix:</b> /app (for client → server messages)</p>
     * 
     * <p><b>Example Topics:</b></p>
     * <ul>
     *   <li>/topic/kds/{tenantId}/{kitchenAreaId} - Updates for specific kitchen area</li>
     *   <li>/topic/kds/{tenantId} - Updates for entire tenant (if needed)</li>
     * </ul>
     * 
     * @param config MessageBrokerRegistry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app")
               .enableSimpleBroker("/topic");
    }
}
