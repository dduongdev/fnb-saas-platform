package com.project.fnb.modules.pos.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO cho Notification API.
 * 
 * <p>Chứa đầy đủ thông tin notification để hiển thị trong UI.</p>
 * 
 * @see com.project.fnb.modules.pos.entity.Notification
 */
@Data
@Builder
public class NotificationResponse {

    private Long id;
    
    /**
     * Loại notification: NEW_ORDER, ADD_ITEM, REMOVE_ITEM, UPDATE_ITEM,
     * PAYMENT_REQUESTED, PAYMENT_SUCCESS, SESSION_CONFIRMED, SESSION_REJECTED
     */
    private String type;
    
    /**
     * Tiêu đề ngắn gọn
     */
    private String title;
    
    /**
     * Nội dung chi tiết
     */
    private String content;
    
    /**
     * Mức độ ưu tiên: HIGH, MEDIUM, LOW
     */
    private String priority;
    
    /**
     * ID của table liên quan (nếu có)
     */
    private String tableId;
    
    /**
     * Tên bàn để hiển thị
     */
    private String tableName;
    
    /**
     * ID của session liên quan (nếu có)
     */
    private Long sessionId;
    
    /**
     * Đã đọc chưa
     */
    private Boolean isRead;
    
    /**
     * Thời gian đọc
     */
    private LocalDateTime readAt;
    
    /**
     * Thời gian tạo notification
     */
    private LocalDateTime createdAt;
}
