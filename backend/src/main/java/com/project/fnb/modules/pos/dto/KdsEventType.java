package com.project.fnb.modules.pos.dto;

/**
 * Enum đại diện cho các loại sự kiện trong Kitchen Display System (KDS).
 * 
 * <p><b>Sự kiện:</b></p>
 * <ul>
 *   <li>SESSION_CREATED: Session mới được tạo</li>
 *   <li>SESSION_CANCELLED: Session bị hủy</li>
 *   <li>ITEM_ADDED: Thêm một OrderItem mới vào session</li>
 *   <li>ITEM_REMOVED: Xóa một OrderItem khỏi session</li>
 *   <li>ITEM_STATUS_CHANGED: Trạng thái một OrderItem thay đổi (VD: PENDING → SERVED)</li>
 *   <li>REFRESH: Refresh toàn bộ danh sách (khi có nhiều thay đổi)</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.0
 */
public enum KdsEventType {
    SESSION_CREATED,
    SESSION_CANCELLED,
    ITEM_ADDED,
    ITEM_REMOVED,
    ITEM_STATUS_CHANGED,
    ITEM_UPDATED,
    REFRESH
}
