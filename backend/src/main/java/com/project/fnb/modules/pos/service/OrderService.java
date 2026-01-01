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
 * OrderService - Chỉ giữ lại các method còn được sử dụng.
 * Các nghiệp vụ chính đã chuyển sang SessionService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Xóa món (Chỉ PENDING - Hard Delete).
     * Được gọi từ SessionController hoặc trực tiếp.
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
     * Notify khi thanh toán thành công (VNPay callback).
     */
    public void notifyPaymentSuccess(Order order) {
        String tenantId = order.getTenantId();
        
        notifyOrderUpdate(order); 

        DiningTable table = order.getPrimaryTable();
        String tableName = table != null ? table.getName() : "N/A";
        Integer tableId = table != null ? table.getId() : null;
        
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