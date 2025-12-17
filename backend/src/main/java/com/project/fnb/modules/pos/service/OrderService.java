package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.hrm.entity.Employee;
import com.project.fnb.modules.hrm.repository.EmployeeRepository;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.repository.ProductRepository;
import com.project.fnb.modules.pos.dto.AddItemRequest;
import com.project.fnb.modules.pos.dto.InvoiceDto;
import com.project.fnb.modules.pos.dto.NotificationMessage;
import com.project.fnb.modules.pos.dto.OrderResponse;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.pos.event.OrderPaidEvent;
import com.project.fnb.modules.pos.repository.OrderItemRepository;
import com.project.fnb.modules.pos.repository.OrderRepository;
import com.project.fnb.modules.pos.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TableRepository tableRepository;
    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmployeeRepository employeeRepository;
    private final TableService tableService; 
    private final TenantRepository tenantRepository;

    private final ApplicationEventPublisher eventPublisher;

    // 1. Mở Phiên
    @Transactional
    public Order createOrGetSession(Integer tableId) {
        DiningTable currentTable = tableRepository.findById(tableId)
                .orElseThrow(() -> new AppException(404, "Bàn không tồn tại"));

        // Logic Gộp: Luôn trỏ về Master
        DiningTable masterTable = currentTable.getMasterTable() != null ? currentTable.getMasterTable() : currentTable;

        return orderRepository.findByTableIdAndStatusWithDetails(masterTable.getId(), Order.OrderStatus.OPEN)
                .orElseGet(() -> {
                    Employee staff = getCurrentStaff();
                    
                    Order newOrder = Order.builder()
                            .table(masterTable)
                            .status(Order.OrderStatus.OPEN)
                            .totalAmount(BigDecimal.ZERO)
                            .createdBy(staff)
                            .build();

                    // Update trạng thái bàn
                    updateTableStatus(masterTable, DiningTable.Status.SERVING);

                    Order savedOrder = orderRepository.save(newOrder);

                    // Bắn thông báo cho nhân viên: "Có khách vào bàn"
                    // Lưu ý: Dùng masterTable.getName() hoặc currentTable.getName() tùy nghiệp vụ
                    sendNotification("NEW_ORDER", masterTable, "Bàn " + masterTable.getName() + " vừa mở phiên mới");

                    return savedOrder;
                });
    }

    // 2. Thêm món
    @Transactional
    public void addItems(Integer tableId, List<AddItemRequest> items) {
        if (items == null || items.isEmpty()) return;

        Order order = createOrGetSession(tableId);
        DiningTable originalTable = tableRepository.findById(tableId).orElse(order.getTable());
        Employee staff = getCurrentStaff();

        for (AddItemRequest req : items) {
            Product product = productRepository.findById(req.getProductId())
                    .orElseThrow(() -> new AppException(404, "Món không tồn tại: " + req.getProductId()));

            if (product.getStatus() == Product.ProductStatus.OUT_OF_STOCK) {
                throw new AppException(400, "Món đã hết hàng: " + product.getName());
            }

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .originalTable(originalTable)
                    .quantity(req.getQuantity())
                    .price(product.getPrice())
                    .note(req.getNote())
                    .status(OrderItem.ItemStatus.PENDING)
                    .createdBy(staff) // Lưu vết ai gọi
                    .build();
            
            orderItemRepository.save(item);

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
            order.setTotalAmount(order.getTotalAmount().add(itemTotal));
        }

        Order savedOrder = orderRepository.save(order);

        // Sync màn hình khách + nhân viên
        notifyTableUpdate(savedOrder);

        // Bắn thông báo cho nhân viên
        sendNotification("NEW_ITEM", savedOrder.getTable(), "Bàn " + savedOrder.getTable().getName() + " vừa gọi thêm món");
    }

    // 3. Xóa món (Chỉ PENDING - Hard Delete)
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

        // Xóa khỏi list trong memory để response socket đúng
        order.getItems().remove(item);

        orderItemRepository.delete(item);
        Order savedOrder = orderRepository.save(order);

        notifyTableUpdate(savedOrder);
    }

    // 4. Hủy Order (Chỉ Nhân viên)
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        // [BẢO MẬT] Kiểm tra quyền nhân viên
        if (getCurrentStaff() == null) {
            throw new AppException(403, "Chỉ nhân viên quán mới có quyền hủy đơn hàng.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(404, "Order not found"));

        if (order.getStatus() != Order.OrderStatus.OPEN) {
            throw new AppException(400, "Chỉ có thể hủy đơn đang phục vụ.");
        }

        // Logic hủy
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
        
        // Reset trạng thái bàn về Trống
        // Gọi splitTable để giải phóng cả nhóm nếu đang gộp
        tableService.splitTable(order.getTable().getId()); 
        
        // Notify để màn hình khách update (bị kick ra hoặc hiện thông báo hủy)
        notifyTableUpdate(order);
    }

    @Transactional
    public InvoiceDto payCash(Long orderId) {
        // [BẢO MẬT] Chỉ nhân viên được xác nhận tiền mặt
        Employee cashier = getCurrentStaff();
        if (cashier == null) {
            throw new AppException(403, "Chỉ nhân viên mới được thực hiện thanh toán.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(404, "Order not found"));

        if (order.getStatus() == Order.OrderStatus.COMPLETED || order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new AppException(400, "Đơn hàng đã kết thúc, không thể thanh toán lại.");
        }

        // 1. Cập nhật Order
        order.setStatus(Order.OrderStatus.COMPLETED);
        order.setPaymentMethod("CASH");
        order.setCompletedAt(LocalDateTime.now());
        
        // Lưu lại
        Order savedOrder = orderRepository.save(order);

        // 2. Giải phóng bàn
        tableService.releaseTable(order.getTable().getId());

        // 3. Notify (Để màn hình chuyển sang trạng thái đã thanh toán/trống)
        notifyTableUpdate(savedOrder);

        eventPublisher.publishEvent(new OrderPaidEvent(savedOrder));

        // 4. Tạo Invoice DTO trả về
        return createInvoice(savedOrder, cashier);
    }

    // Helper tạo hóa đơn
    private InvoiceDto createInvoice(Order order, Employee cashier) {
        // Lấy thông tin quán
        String tenantId = TenantContext.getTenantId();
        var tenant = tenantRepository.findById(tenantId).orElseThrow();

        List<InvoiceDto.InvoiceItemDto> items = order.getItems().stream()
                .map(i -> InvoiceDto.InvoiceItemDto.builder()
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .price(i.getPrice())
                        .total(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .build())
                .toList();

        return InvoiceDto.builder()
                .tenantName(tenant.getName())
                .tenantAddress(tenant.getAddress())
                .tenantLogo(tenant.getLogoUrl())
                .orderId(order.getId())
                .tableName(order.getTable().getName())
                .checkInTime(order.getCreatedAt())
                .checkOutTime(order.getCompletedAt())
                .cashierName(cashier.getUser().getFullName()) // Lấy tên thật từ User
                .items(items)
                .totalAmount(order.getTotalAmount())
                .paymentMethod("CASH")
                .build();
    }

    // --- Helpers ---

    private void updateTableStatus(DiningTable table, DiningTable.Status status) {
        if (table.getStatus() != status) {
            table.setStatus(status);
            tableRepository.save(table);
        }
    }

    private void notifyTableUpdate(Order order) {
        String tenantId = TenantContext.getTenantId();
        try {
            // Topic sync data: /topic/tenant/{id}/table/{tableId}
            String topic = "/topic/tenant/" + tenantId + "/table/" + order.getTable().getId();
            
            OrderResponse payload = OrderResponse.fromEntity(order);
            messagingTemplate.convertAndSend(topic, payload);
        } catch (Exception e) {
            log.error("Socket update error", e);
        }
    }

    private void sendNotification(String type, DiningTable table, String content) {
        String tenantId = TenantContext.getTenantId();
        // Topic notification: /topic/tenant/{id}/notifications
        String topic = "/topic/tenant/" + tenantId + "/notifications";

        // [FIX] Build object đầy đủ
        NotificationMessage msg = NotificationMessage.builder()
                .type(type)
                .title("Thông báo mới")
                .content(content)
                .tableId(table.getId())
                .tableName(table.getName())
                .build();

        messagingTemplate.convertAndSend(topic, msg);
    }

    private Employee getCurrentStaff() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
                return null;
            }
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String userId = jwt.getSubject();
            String tenantId = TenantContext.getTenantId();

            return employeeRepository.findByUserIdAndTenantId(userId, tenantId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public void notifyPaymentSuccess(Order order) {
        String tenantId = order.getTenantId(); // Lưu ý: Lúc này Context đã được set trong Controller
        
        // 1. Sync Data: Báo cho màn hình bàn đó biết là đã thanh toán xong (để reset giao diện)
        // Topic: /topic/tenant/{id}/table/{tableId}
        // Gửi OrderResponse mới nhất (đã COMPLETED)
        notifyTableUpdate(order); 

        // 2. Alert: Báo cho nhân viên biết tiền đã về
        // Topic: /topic/tenant/{id}/notifications
        String content = String.format("Đơn hàng #%d tại Bàn %s đã thanh toán thành công qua VNPay (%s)", 
                order.getId(), 
                order.getTable().getName(), 
                order.getTotalAmount());

        NotificationMessage msg = NotificationMessage.builder()
                .type("PAYMENT_SUCCESS")
                .title("Thanh toán thành công")
                .content(content)
                .tableId(order.getTable().getId())
                .tableName(order.getTable().getName())
                .build();

        String notificationTopic = "/topic/tenant/" + tenantId + "/notifications";
        messagingTemplate.convertAndSend(notificationTopic, msg);
    }
}