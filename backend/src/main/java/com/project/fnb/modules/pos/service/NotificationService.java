package com.project.fnb.modules.pos.service;

import com.project.fnb.modules.pos.dto.NotificationMessage;
import com.project.fnb.modules.pos.dto.NotificationResponse;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.Notification;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.repository.NotificationRepository;
import com.project.fnb.infrastructure.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service quản lý notifications cho chủ quán/nhân viên.
 * 
 * <p><b>Chức năng:</b></p>
 * <ul>
 *   <li>Lưu notification vào database</li>
 *   <li>Gửi notification realtime qua WebSocket</li>
 *   <li>Quản lý trạng thái đọc/chưa đọc</li>
 *   <li>Phân loại notifications theo type/priority</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Tạo và gửi notification mới.
     * 
     * @param type Loại notification (NEW_ORDER, ADD_ITEM, etc.)
     * @param title Tiêu đề ngắn gọn
     * @param content Nội dung chi tiết
     * @param priority Mức độ ưu tiên
     * @param session Session liên quan (có thể null)
     * @param table Table liên quan (có thể null)
     */
    @Transactional
    public void createAndSend(String type, String title, String content, 
                              Notification.Priority priority,
                              ServingSession session, DiningTable table) {
        // 1. Lưu vào database
        Notification notification = Notification.builder()
                .type(type)
                .title(title)
                .content(content)
                .priority(priority)
                .session(session)
                .table(table)
                .tableName(table != null ? table.getName() : 
                          (session != null ? session.getTableNames() : null))
                .isRead(false)
                .build();
        notification = notificationRepository.save(notification);

        // 2. Gửi qua WebSocket
        sendToWebSocket(notification);
        
        log.info("[Notification] Created: type={}, title={}, table={}", 
                type, title, notification.getTableName());
    }

    /**
     * Gửi notification nhanh với priority mặc định MEDIUM.
     */
    @Transactional
    public void createAndSend(String type, String title, String content,
                              ServingSession session, DiningTable table) {
        createAndSend(type, title, content, Notification.Priority.MEDIUM, session, table);
    }

    /**
     * Gửi notification cho các sự kiện quan trọng với priority HIGH.
     */
    @Transactional
    public void createHighPriority(String type, String title, String content,
                                   ServingSession session, DiningTable table) {
        createAndSend(type, title, content, Notification.Priority.HIGH, session, table);
    }

    /**
     * Gửi notification qua WebSocket.
     */
    private void sendToWebSocket(Notification notification) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.warn("[Notification] No tenant context, cannot send WebSocket");
            return;
        }

        NotificationMessage message = NotificationMessage.builder()
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .tableId(notification.getTable() != null ? notification.getTable().getId() : null)
                .tableName(notification.getTableName())
                .build();

        // Thêm các fields bổ sung cho frontend
        NotificationResponse response = NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .priority(notification.getPriority().name())
                .tableId(notification.getTable() != null ? notification.getTable().getId() : null)
                .tableName(notification.getTableName())
                .sessionId(notification.getSession() != null ? notification.getSession().getId() : null)
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();

        String topic = "/topic/tenant/" + tenantId + "/notifications";
        messagingTemplate.convertAndSend(topic, response);
        log.debug("[WS] Sent notification to {}: {}", topic, notification.getType());
    }

    /**
     * Lấy danh sách notifications với phân trang.
     */
    public Page<NotificationResponse> getNotifications(Pageable pageable) {
        return notificationRepository.findAllByTenantOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    /**
     * Lấy notifications chưa đọc.
     */
    public List<NotificationResponse> getUnreadNotifications() {
        return notificationRepository.findUnreadNotifications().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Đếm số notifications chưa đọc.
     */
    public Long countUnread() {
        return notificationRepository.countUnreadNotifications();
    }

    /**
     * Lấy notifications gần đây (cho dropdown).
     */
    public List<NotificationResponse> getRecentNotifications(int limit) {
        return notificationRepository.findRecentNotifications(PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Đánh dấu notification đã đọc.
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.markAsRead(notificationId, LocalDateTime.now());
    }

    /**
     * Đánh dấu tất cả notifications đã đọc.
     */
    @Transactional
    public void markAllAsRead() {
        notificationRepository.markAllAsRead(LocalDateTime.now());
    }

    /**
     * Convert entity to response DTO.
     */
    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .content(n.getContent())
                .priority(n.getPriority().name())
                .tableId(n.getTable() != null ? n.getTable().getId() : null)
                .tableName(n.getTableName())
                .sessionId(n.getSession() != null ? n.getSession().getId() : null)
                .isRead(n.getIsRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
