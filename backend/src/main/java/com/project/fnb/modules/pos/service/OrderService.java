package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.dto.NotificationMessage;
import com.project.fnb.modules.pos.dto.OrderResponse;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.pos.repository.OrderItemRepository;
import com.project.fnb.modules.pos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service xử lý các nghiệp vụ liên quan đến Order còn sử dụng.
 * 
 * <p><b>Architecture Note:</b> Đa số nghiệp vụ order đã được chuyển sang
 * {@link SessionService} theo mô hình Session-based. OrderService chỉ giữ lại
 * một số helper methods còn được sử dụng.</p>
 * 
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Xóa món (removeItem) - được gọi từ SessionController</li>
 *   <li>Thông báo thanh toán thành công (notifyPaymentSuccess) - callback từ VNPay</li>
 *   <li>WebSocket notifications cho order updates</li>
 * </ul>
 * 
 * @deprecated Cân nhắc merge vào SessionService trong tương lai
 * @see SessionService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Xóa món khỏi order (hard delete).
     * 
     * <p><b>Business Constraint:</b> Chỉ cho phép xóa món ở trạng thái PENDING.
     * Món đã chế biến (PREPARING) hoặc ra đồ (SERVED) không được xóa.</p>
     * 
     * <p><b>Price Recalculation:</b> Tự động trừ tiền món bị xóa khỏi totalAmount của Order.</p>
     * 
     * <p><b>WebSocket:</b> Push event qua topic {@code /topic/tenant/{tenantId}/table/{tableId}}
     * để cập nhật UI realtime.</p>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Khách đổi ý, muốn xóa món vừa thêm</li>
     *   <li>Nhân viên gọi nhầm món, cần xóa trước khi bếp chế biến</li>
     * </ul>
     * 
     * @param itemId ID của OrderItem cần xóa
     * @throws AppException 404 nếu item không tồn tại
     * @throws AppException 400 nếu item không ở trạng thái PENDING
     */
    @Transactional
    public void removeItem(Long itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(404, "Món không tồn tại"));

        if (item.getStatus() != OrderItem.ItemStatus.PENDING) {
            throw new AppException(400, "Món đã chế biến/ra đồ, không thể xóa.");
        }

        Order order = item.getOrder();

        BigDecimal deductAmount = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        order.setTotalAmount(order.getTotalAmount().subtract(deductAmount));

        order.getItems().remove(item);
        orderItemRepository.delete(item);
        orderRepository.save(order);

        notifyOrderUpdate(order);
    }

    /**
     * Thông báo thanh toán thành công qua WebSocket sau khi VNPay callback.
     * 
     * <p><b>Context:</b> Method này được gọi từ VNPay payment callback handler
     * sau khi giao dịch thành công.</p>
     * 
     * <p><b>WebSocket Topics:</b></p>
     * <ul>
     *   <li>{@code /topic/tenant/{tenantId}/table/{tableId}} - Order update cho table</li>
     *   <li>{@code /topic/tenant/{tenantId}/notifications} - Notification cho toàn bộ staff</li>
     * </ul>
     * 
     * <p><b>Notification Content:</b> "Order #{orderId} tại Bàn {tableName} đã thanh toán thành công
     * qua VNPay ({amount})"</p>
     * 
     * @param order Order vừa thanh toán thành công
     */
    public void notifyPaymentSuccess(Order order) {
        String tenantId = order.getTenantId();
        
        notifyOrderUpdate(order); 

        DiningTable table = order.getPrimaryTable();
        String tableName = table != null ? table.getName() : "N/A";
        String tableId = table != null ? table.getId() : null;
        
        String content = String.format("Đơn hàng #%d tại Bàn %s đã thanh toán thành công qua VNPay (%s)", 
                order.getId(), 
                tableName, 
                order.getTotalAmount());

        NotificationMessage msg = NotificationMessage.builder()
                .type("PAYMENT_SUCCESS")
                .title("Thanh toán thành công")
                .content(content)
                .tableId(tableId)
                .tableName(tableName)
                .build();

        String notificationTopic = "/topic/tenant/" + tenantId + "/notifications";
        messagingTemplate.convertAndSend(notificationTopic, msg);
    }

    private void notifyOrderUpdate(Order order) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = order.getTenantId();
        }
        try {
            DiningTable table = order.getPrimaryTable();
            if (table == null) return;
            
            String topic = "/topic/tenant/" + tenantId + "/table/" + table.getId();
            OrderResponse payload = OrderResponse.fromEntity(order);
            messagingTemplate.convertAndSend(topic, payload);
        } catch (Exception e) {
            log.error("Socket update error", e);
        }
    }
}