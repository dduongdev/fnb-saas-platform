package com.project.fnb.modules.pos.dto;

import lombok.*;

/**
 * DTO đại diện cho một sự kiện cập nhật trên KDS gửi qua WebSocket.
 * 
 * <p><b>Cấu trúc:</b> Chứa loại sự kiện, dữ liệu liên quan, và timestamp.</p>
 * 
 * @author FNB Team
 * @version 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KdsUpdatePayload {
    
    /**
     * Loại sự kiện (SESSION_CREATED, ITEM_ADDED, v.v...).
     */
    private KdsEventType eventType;
    
    /**
     * Dữ liệu liên quan đến sự kiện.
     * - Nếu eventType = SESSION_CREATED hoặc SESSION_CANCELLED: null hoặc sessionId
     * - Nếu eventType = ITEM_ADDED/REMOVED/STATUS_CHANGED: KdsOrderItemDto hoặc sessionId
     * - Nếu eventType = REFRESH: null (frontend sẽ reload toàn bộ)
     */
    private Object data;
    
    /**
     * Thời điểm sự kiện xảy ra.
     */
    private Long timestamp;
    
    /**
     * ID của Session liên quan (nếu có).
     */
    private Long sessionId;
}
