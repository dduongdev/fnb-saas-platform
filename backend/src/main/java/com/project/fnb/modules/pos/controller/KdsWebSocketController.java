package com.project.fnb.modules.pos.controller;

import com.project.fnb.modules.pos.dto.KdsSessionDto;
import com.project.fnb.modules.pos.dto.KdsUpdatePayload;
import com.project.fnb.modules.pos.service.KdsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * WebSocket Controller cho Kitchen Display System (KDS).
 * 
 * <p><b>Mục đích:</b> Xử lý STOMP subscriptions từ KDS clients và gửi initial data.</p>
 * 
 * <p><b>Message Flow:</b></p>
 * <pre>
 * Client:
 *   SUBSCRIBE /app/kds/subscribe/{tenantId}/{kitchenAreaId}
 *   ↓
 * Server (KdsWebSocketController):
 *   handleSubscribe() receives & validates subscription
 *   ↓
 *   Calls KdsService.getAllActiveSessions()
 *   ↓
 *   Sends initial data to /topic/kds/{tenantId}/{kitchenAreaId}
 *   ↓
 *   Client automatically subscribed to /topic/kds/{tenantId}/{kitchenAreaId}
 *   receives all future updates via KdsEventPublisher
 * </pre>
 * 
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>SUBSCRIBE: /app/kds/subscribe/{tenantId}/{kitchenAreaId}</li>
 *   <li>Topic (Server → Client): /topic/kds/{tenantId}/{kitchenAreaId}</li>
 * </ul>
 * 
 * <p><b>Note:</b> STOMP subscription returns no response automatically.
 * Our handleSubscribe() explicitly sends initial data via messagingTemplate.convertAndSend().</p>
 * 
 * @author FNB Team
 * @version 1.0
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class KdsWebSocketController {
    
    private final KdsService kdsService;
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * Xử lý subscription request từ KDS client.
     * 
     * <p><b>Khi client kết nối:</b></p>
     * <ol>
     *   <li>Client gửi SUBSCRIBE message tới /app/kds/subscribe/{tenantId}/{kitchenAreaId}</li>
     *   <li>Spring routes tới method này</li>
     *   <li>Method lấy tất cả active sessions từ KdsService</li>
     *   <li>Send initial data tới /topic/kds/{tenantId}/{kitchenAreaId}</li>
     *   <li>Client tự động nhận được message này (vì đã subscribe tới topic)</li>
     *   <li>Các event update sau này sẽ được gửi bởi KdsEventPublisher</li>
     * </ol>
     * 
     * <p><b>Parameters:</b></p>
     * <ul>
     *   <li>{@code tenantId} - Tenant ID để isolate dữ liệu</li>
     *   <li>{@code kitchenAreaId} - Optional kitchen area ID (có thể null)</li>
     * </ul>
     * 
     * @param tenantId          Tenant ID
     * @param kitchenAreaId     Kitchen Area ID (tùy chọn)
     */
    @MessageMapping("/kds/subscribe/{tenantId}/{kitchenAreaId}")
    @com.project.fnb.aspect.RequirePermission({
            com.project.fnb.aspect.OwnerPermissionValidator.class,
            com.project.fnb.aspect.KitchenPermissionValidator.class,
            com.project.fnb.aspect.WaiterPermissionValidator.class
    })
    public void handleSubscribe(
            @DestinationVariable String tenantId,
            @DestinationVariable String kitchenAreaId) {
        
        try {
            log.info("Client subscribed to KDS: tenantId={}, kitchenAreaId={}", tenantId, kitchenAreaId);
            
            // Thiết lập tenant context cho WebSocket message (quan trọng để filter multi-tenant)
            com.project.fnb.infrastructure.security.TenantContext.setTenantId(tenantId);
            
            // Lấy tất cả active sessions
            List<KdsSessionDto> sessions = kdsService.getAllActiveSessions();
            
            // Tạo payload chứa toàn bộ danh sách sessions
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(com.project.fnb.modules.pos.dto.KdsEventType.REFRESH)
                    .data(sessions)  // Send list of all active sessions
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            // Gửi initial data tới topic
            String destination = buildTopicDestination(tenantId, kitchenAreaId);
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Sent initial KDS data ({} sessions) to {}", sessions.size(), destination);
        } catch (Exception e) {
            log.error("Error handling KDS subscription", e);
        } finally {
            // Xóa TenantContext để tránh leak giữa các kết nối websocket khác nhau
            com.project.fnb.infrastructure.security.TenantContext.clear();
        }
    }
    
    /**
     * Build topic destination string: /topic/kds/{tenantId}/{kitchenAreaId}
     * 
     * <p>Nếu kitchenAreaId là null, tạo topic cho toàn tenant.</p>
     * 
     * @param tenantId Tenant ID
     * @param kitchenAreaId Kitchen Area ID (có thể null)
     * @return Topic destination
     */
    private String buildTopicDestination(String tenantId, String kitchenAreaId) {
        if (kitchenAreaId != null && !kitchenAreaId.isEmpty() && !"null".equals(kitchenAreaId)) {
            if ("ALL".equalsIgnoreCase(kitchenAreaId)) {
                return "/topic/kds/" + tenantId;
            }
            return "/topic/kds/" + tenantId + "/" + kitchenAreaId;
        }
        return "/topic/kds/" + tenantId;
    }
}
