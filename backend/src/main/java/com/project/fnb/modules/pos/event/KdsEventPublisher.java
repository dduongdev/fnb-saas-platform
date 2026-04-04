package com.project.fnb.modules.pos.event;

import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.dto.KdsEventType;
import com.project.fnb.modules.pos.dto.KdsOrderItemDto;
import com.project.fnb.modules.pos.dto.KdsSessionDto;
import com.project.fnb.modules.pos.dto.KdsUpdatePayload;
import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.service.KdsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Component phát hành các sự kiện KDS qua WebSocket.
 * 
 * <p><b>Mục đích:</b> Khi có thay đổi trên backend (session created, item added, etc.),
 * KdsEventPublisher sẽ publish events qua WebSocket để tất cả client KDS nhận được cập nhật realtime.</p>
 * 
 * <p><b>Integration Points:</b> Các service khác (OrderService, SessionService) sẽ gọi các method
 * của KdsEventPublisher sau khi thực hiện thao tác (VD: sau khi xóa item, gọi publishItemRemoved()).</p>
 * 
 * <p><b>Event Types:</b></p>
 * <ul>
 *   <li>SESSION_CREATED: Session mới được tạo → frontend thêm cột mới</li>
 *   <li>SESSION_CANCELLED: Session bị hủy → frontend xóa cột</li>
 *   <li>ITEM_ADDED: Thêm OrderItem → frontend thêm card vào cột</li>
 *   <li>ITEM_REMOVED: Xóa OrderItem → frontend xóa card khỏi cột</li>
 *   <li>ITEM_STATUS_CHANGED: Item status thay đổi (PENDING→SERVED) → frontend sắp xếp lại</li>
 *   <li>REFRESH: Refresh toàn bộ danh sách → frontend reload all data</li>
 * </ul>
 * 
 * <p><b>Topic Structure:</b></p>
 * <pre>
 *   /topic/kds/{tenantId}/{kitchenAreaId}
 * </pre>
 * Mỗi kitchen area có topic riêng để isolate dữ liệu.
 * 
 * @author FNB Team
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KdsEventPublisher {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final KdsService kdsService;
    
    /**
     * Publish event khi Session mới được tạo.
     * 
     * <p><b>Frontend Action:</b> Thêm cột mới với thông tin session.</p>
     * 
     * @param session ServingSession vừa được tạo
     * @param kitchenAreaId ID của kitchen area (có thể null → broadcast cho tất cả)
     */
    public void publishSessionCreated(ServingSession session, String kitchenAreaId) {
        try {
            String tenantId = session.getTenantId();
            KdsSessionDto sessionDto = transformToDto(session);
            
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.SESSION_CREATED)
                    .data(sessionDto)
                    .sessionId(session.getId())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = buildTopicDestination(tenantId, kitchenAreaId);
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published SESSION_CREATED event for session {} to {}", session.getId(), destination);
        } catch (Exception e) {
            log.error("Error publishing SESSION_CREATED event", e);
        }
    }
    
    /**
     * Publish event khi Session bị hủy.
     * 
     * <p><b>Frontend Action:</b> Xóa cột của session.</p>
     * 
     * @param sessionId ID của session bị hủy
     * @param tenantId Tenant ID
     * @param kitchenAreaId ID của kitchen area
     */
    public void publishSessionCancelled(Long sessionId, String tenantId, String kitchenAreaId) {
        try {
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.SESSION_CANCELLED)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = buildTopicDestination(tenantId, kitchenAreaId);
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published SESSION_CANCELLED event for session {} to {}", sessionId, destination);
        } catch (Exception e) {
            log.error("Error publishing SESSION_CANCELLED event", e);
        }
    }
    
    /**
     * Publish event khi OrderItem mới được thêm vào session.
     * 
     * <p><b>Frontend Action:</b> Thêm card của item vào cột tương ứng.</p>
     * 
     * @param item OrderItem vừa được thêm
     * @param sessionId ID của session chứa item
     * @param kitchenAreaId ID của kitchen area
     */
    public void publishItemAdded(OrderItem item, Long sessionId, String kitchenAreaId) {
        try {
            String tenantId = item.getTenantId();
            KdsOrderItemDto itemDto = transformItemToDto(item);
            
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.ITEM_ADDED)
                    .data(itemDto)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = buildTopicDestination(tenantId, kitchenAreaId);
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published ITEM_ADDED event for item {} to {}", item.getId(), destination);
        } catch (Exception e) {
            log.error("Error publishing ITEM_ADDED event", e);
        }
    }
    
    /**
     * Publish event khi OrderItem bị xóa.
     * 
     * <p><b>Frontend Action:</b> Xóa card của item khỏi cột.</p>
     * 
     * @param itemId ID của OrderItem bị xóa
     * @param sessionId ID của session chứa item
     * @param tenantId Tenant ID
     * @param kitchenAreaId ID của kitchen area
     */
    public void publishItemRemoved(Long itemId, Long sessionId, String tenantId, String kitchenAreaId) {
        try {
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.ITEM_REMOVED)
                    .data(itemId)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = buildTopicDestination(tenantId, kitchenAreaId);
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published ITEM_REMOVED event for item {} to {}", itemId, destination);
        } catch (Exception e) {
            log.error("Error publishing ITEM_REMOVED event", e);
        }
    }
    
    /**
     * Publish event khi OrderItem status thay đổi (VD: PENDING → SERVED).
     * 
     * <p><b>Frontend Action:</b> Cập nhật status của card và sắp xếp lại (move to bottom).</p>
     * 
     * @param item OrderItem với status mới
     * @param sessionId ID của session chứa item
     * @param kitchenAreaId ID của kitchen area
     */
    public void publishItemStatusChanged(OrderItem item, Long sessionId, String kitchenAreaId) {
        try {
            String tenantId = item.getTenantId();
            KdsOrderItemDto itemDto = transformItemToDto(item);
            
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.ITEM_STATUS_CHANGED)
                    .data(itemDto)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = buildTopicDestination(tenantId, kitchenAreaId);
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published ITEM_STATUS_CHANGED event for item {}", item.getId());
        } catch (Exception e) {
            log.error("Error publishing ITEM_STATUS_CHANGED event", e);
        }
    }

    public void publishItemUpdated(OrderItem item, Long sessionId, String kitchenAreaId) {
        try {
            String tenantId = item.getTenantId();
            KdsOrderItemDto itemDto = transformItemToDto(item);

            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.ITEM_UPDATED)
                    .data(itemDto)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            String destination = buildTopicDestination(tenantId, kitchenAreaId);
            messagingTemplate.convertAndSend(destination, payload);

            log.info("Published ITEM_UPDATED event for item {}", item.getId());
        } catch (Exception e) {
            log.error("Error publishing ITEM_UPDATED event", e);
        }
    }
    
    /**
     * Publish REFRESH event để frontend reload toàn bộ data.
     * 
     * <p><b>Use Case:</b> Khi có lỗi desync hoặc quá nhiều thay đổi.</p>
     * 
     * @param tenantId Tenant ID
     * @param kitchenAreaId ID của kitchen area
     */
    public void publishRefresh(String tenantId, String kitchenAreaId) {
        try {
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.REFRESH)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = buildTopicDestination(tenantId, kitchenAreaId);
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published REFRESH event to {}", destination);
        } catch (Exception e) {
            log.error("Error publishing REFRESH event", e);
        }
    }
    
    /**
     * Build topic destination string: /topic/kds/{tenantId}/{kitchenAreaId}
     * 
     * <p>Nếu kitchenAreaId là null/empty, sẽ broadcast cho toàn tenant, để frontend filter.</p>
     * 
     * @param tenantId Tenant ID
     * @param kitchenAreaId Kitchen Area ID (có thể null)
     * @return Topic destination
     */
    private String buildTopicDestination(String tenantId, String kitchenAreaId) {
        if (kitchenAreaId != null && !kitchenAreaId.isEmpty()) {
            return "/topic/kds/" + tenantId + "/" + kitchenAreaId;
        }
        return "/topic/kds/" + tenantId;
    }
    
    /**
     * Transform ServingSession entity → KdsSessionDto.
     * 
     * @param session ServingSession
     * @return KdsSessionDto
     */
    private KdsSessionDto transformToDto(ServingSession session) {
        // Delegate to KdsService
        return kdsService.getAllActiveSessions().stream()
                .filter(s -> s.getSessionId().equals(session.getId()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Transform OrderItem entity → KdsOrderItemDto.
     * 
     * @param item OrderItem
     * @return KdsOrderItemDto
     */
    private KdsOrderItemDto transformItemToDto(OrderItem item) {
        String originalTableName = item.getOriginalTable() != null
                ? (item.getOriginalTable().getName() != null
                    ? item.getOriginalTable().getName()
                    : "Bàn " + item.getOriginalTable().getId())
                : null;
        
        String status = item.getStatus() != null ? item.getStatus().toString() : "PENDING";
        
        return KdsOrderItemDto.builder()
                .itemId(item.getId())
                .productName(item.getProduct() != null ? item.getProduct().getName() : "Unknown Product")
                .quantity(item.getQuantity())
                .notes(item.getNote())
                .status(status)
                .createdAt(item.getCreatedAt())
                .originalTableName(originalTableName)
                .build();
    }
}
