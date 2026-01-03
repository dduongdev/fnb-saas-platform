package com.project.fnb.modules.pos.dto;

import lombok.Builder;
import lombok.Data;

/**
 * WebSocket message để gửi notifications realtime cho staff.
 * 
 * <p><b>WebSocket Topic:</b> {@code /topic/tenant/{tenantId}/notifications}</p>
 * 
 * <p><b>Notification Types:</b></p>
 * <ul>
 *   <li>CUSTOMER_ORDER_CREATED - Khách order qua QR</li>
 *   <li>PAYMENT_SUCCESS - Thanh toán thành công qua VNPay</li>
 *   <li>PAYMENT_REQUESTED - Khách yêu cầu thanh toán</li>
 *   <li>TABLE_CALLED - Khách gọi nhân viên</li>
 * </ul>
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li>type: Loại notification</li>
 *   <li>title: Tiêu đề ngắn gọn</li>
 *   <li>content: Nội dung chi tiết</li>
 *   <li>tableId: ID bàn liên quan (optional)</li>
 *   <li>tableName: Tên bàn hiển thị (optional)</li>
 * </ul>
 * 
 * <p><b>Example:</b></p>
 * <pre>
 * NotificationMessage.builder()
 *     .type("CUSTOMER_ORDER_CREATED")
 *     .title("Order mới từ khách")
 *     .content("Bàn 05 vừa order 3 món qua QR")
 *     .tableId(5)
 *     .tableName("Bàn 05")
 *     .build();
 * </pre>
 * 
 * @see com.project.fnb.modules.pos.service.SessionService
 */
@Data
@Builder
public class NotificationMessage {
    private String type; 
    private String title;
    private String content;
    private String tableId;
    private String tableName;
}