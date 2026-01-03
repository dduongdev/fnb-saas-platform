package com.project.fnb.modules.pos.event;

import com.project.fnb.modules.pos.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Event được publish khi Order thanh toán thành công.
 * 
 * <p><b>Event Flow:</b></p>
 * <ol>
 *   <li>User thanh toán qua VNPay</li>
 *   <li>VNPay callback về, SessionService xử lý và cập nhật order status</li>
 *   <li>SessionService publish OrderPaidEvent</li>
 *   <li>Event listeners (VD: ReportService, InvoiceService) xử lý event</li>
 * </ol>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Gửi notification cho staff</li>
 *   <li>Tạo invoice tự động</li>
 *   <li>Cập nhật báo cáo doanh thu realtime</li>
 *   <li>Trigger loyalty points nếu có</li>
 * </ul>
 * 
 * @see Order
 * @see com.project.fnb.modules.pos.service.SessionService
 */
@Getter
@AllArgsConstructor
public class OrderPaidEvent {
    private final Order order;
}