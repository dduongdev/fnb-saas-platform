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
 * </ul>
 * 
 * <p><b>Topic Structure:</b></p>
 * <pre>
 *   /topic/kds/{tenantId}
 * </pre>
 * Mỗi tenant có 1 kitchen duy nhất nên topic chỉ cần phân chia theo tenantId.
 * 
 * @author FNB Team
 * @version 2.0
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
     */
    public void publishSessionCreated(ServingSession session) {
        try {
            String tenantId = session.getTenantId();
            KdsSessionDto sessionDto = transformToDto(session);
            
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.SESSION_CREATED)
                    .data(sessionDto)
                    .sessionId(session.getId())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = "/topic/kds/" + tenantId;
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published SESSION_CREATED event for session {} to {}", session.getId(), destination);
        } catch (Exception e) {
            log.error("Error publishing SESSION_CREATED event", e);
        }
    }

    /**
     * @deprecated Use {@link #publishSessionCreated(ServingSession)} instead.
     */
    @Deprecated
    public void publishSessionCreated(ServingSession session, String kitchenAreaId) {
        publishSessionCreated(session);
    }
    
    /**
     * Publish event khi Session bị hủy.
     * 
     * <p><b>Frontend Action:</b> Xóa cột của session.</p>
     * 
     * @param sessionId ID của session bị hủy
     * @param tenantId Tenant ID
     */
    public void publishSessionCancelled(Long sessionId, String tenantId) {
        try {
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.SESSION_CANCELLED)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = "/topic/kds/" + tenantId;
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published SESSION_CANCELLED event for session {} to {}", sessionId, destination);
        } catch (Exception e) {
            log.error("Error publishing SESSION_CANCELLED event", e);
        }
    }

    /**
     * @deprecated Use {@link #publishSessionCancelled(Long, String)} instead.
     */
    @Deprecated
    public void publishSessionCancelled(Long sessionId, String tenantId, String kitchenAreaId) {
        publishSessionCancelled(sessionId, tenantId);
    }
    
    /**
     * Publish event khi OrderItem mới được thêm vào session.
     * 
     * <p><b>Frontend Action:</b> Thêm card của item vào cột tương ứng.</p>
     * 
     * @param item OrderItem vừa được thêm
     * @param sessionId ID của session chứa item
     */
    public void publishItemAdded(OrderItem item, Long sessionId) {
        try {
            String tenantId = item.getTenantId();
            KdsOrderItemDto itemDto = transformItemToDto(item);
            
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.ITEM_ADDED)
                    .data(itemDto)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = "/topic/kds/" + tenantId;
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published ITEM_ADDED event for item {} to {}", item.getId(), destination);
        } catch (Exception e) {
            log.error("Error publishing ITEM_ADDED event", e);
        }
    }

    /**
     * @deprecated Use {@link #publishItemAdded(OrderItem, Long)} instead.
     */
    @Deprecated
    public void publishItemAdded(OrderItem item, Long sessionId, String kitchenAreaId) {
        publishItemAdded(item, sessionId);
    }
    
    /**
     * Publish event khi OrderItem bị xóa.
     * 
     * <p><b>Frontend Action:</b> Xóa card của item khỏi cột.</p>
     * 
     * @param itemId ID của OrderItem bị xóa
     * @param sessionId ID của session chứa item
     * @param tenantId Tenant ID
     */
    public void publishItemRemoved(Long itemId, Long sessionId, String tenantId) {
        try {
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.ITEM_REMOVED)
                    .data(itemId)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = "/topic/kds/" + tenantId;
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published ITEM_REMOVED event for item {} to {}", itemId, destination);
        } catch (Exception e) {
            log.error("Error publishing ITEM_REMOVED event", e);
        }
    }

    /**
     * @deprecated Use {@link #publishItemRemoved(Long, Long, String)} instead.
     */
    @Deprecated
    public void publishItemRemoved(Long itemId, Long sessionId, String tenantId, String kitchenAreaId) {
        publishItemRemoved(itemId, sessionId, tenantId);
    }
    
    /**
     * Publish event khi OrderItem status thay đổi (VD: PENDING → SERVED).
     * 
     * <p><b>Frontend Action:</b> Cập nhật status của card và sắp xếp lại (move to bottom).</p>
     * 
     * @param item OrderItem với status mới
     * @param sessionId ID của session chứa item
     */
    public void publishItemStatusChanged(OrderItem item, Long sessionId) {
        try {
            String tenantId = item.getTenantId();
            KdsOrderItemDto itemDto = transformItemToDto(item);
            
            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.ITEM_STATUS_CHANGED)
                    .data(itemDto)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            String destination = "/topic/kds/" + tenantId;
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Published ITEM_STATUS_CHANGED event for item {}", item.getId());
        } catch (Exception e) {
            log.error("Error publishing ITEM_STATUS_CHANGED event", e);
        }
    }

    /**
     * @deprecated Use {@link #publishItemStatusChanged(OrderItem, Long)} instead.
     */
    @Deprecated
    public void publishItemStatusChanged(OrderItem item, Long sessionId, String kitchenAreaId) {
        publishItemStatusChanged(item, sessionId);
    }

    /**
     * Publish event khi OrderItem được cập nhật (VD: quantity thay đổi).
     *
     * @param item OrderItem đã cập nhật
     * @param sessionId ID của session chứa item
     */
    public void publishItemUpdated(OrderItem item, Long sessionId) {
        try {
            String tenantId = item.getTenantId();
            KdsOrderItemDto itemDto = transformItemToDto(item);

            KdsUpdatePayload payload = KdsUpdatePayload.builder()
                    .eventType(KdsEventType.ITEM_UPDATED)
                    .data(itemDto)
                    .sessionId(sessionId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            String destination = "/topic/kds/" + tenantId;
            messagingTemplate.convertAndSend(destination, payload);

            log.info("Published ITEM_UPDATED event for item {}", item.getId());
        } catch (Exception e) {
            log.error("Error publishing ITEM_UPDATED event", e);
        }
    }

    /**
     * @deprecated Use {@link #publishItemUpdated(OrderItem, Long)} instead.
     */
    @Deprecated
    public void publishItemUpdated(OrderItem item, Long sessionId, String kitchenAreaId) {
        publishItemUpdated(item, sessionId);
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
