package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.hrm.entity.Employee;
import com.project.fnb.modules.hrm.repository.EmployeeRepository;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.repository.ProductRepository;
import com.project.fnb.modules.pos.dto.*;
import com.project.fnb.modules.pos.entity.*;
import com.project.fnb.modules.pos.event.OrderPaidEvent;
import com.project.fnb.modules.pos.repository.*;
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
import java.util.HashSet;
import java.util.List;

/**
 * Service quản lý Session (phiên phục vụ).
 * 
 * <p>
 * Session-based design thay thế việc gắn Order trực tiếp vào Table.
 * Một Session đại diện cho một lần phục vụ khách hàng.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantRepository tenantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 1. MỞ BÀN (TẠO SESSION) ====================

    /**
     * Mở bàn / Tạo session mới.
     * Nếu bàn đã có session đang active thì trả về session đó.
     */
    @Transactional
    public ServingSession openTable(SessionRequest.OpenSession request) {
        DiningTable table = tableRepository.findById(request.getTableId())
                .orElseThrow(() -> new AppException(404, "Bàn không tồn tại"));

        // Nếu bàn đã có session → trả về session đó
        if (table.getCurrentSession() != null) {
            return sessionRepository.findByIdWithDetails(table.getCurrentSession().getId())
                    .orElseThrow();
        }

        Employee staff = getCurrentStaff();

        // Tạo session mới
        ServingSession session = ServingSession.builder()
                .status(ServingSession.SessionStatus.ACTIVE)
                .startedAt(LocalDateTime.now())
                .guestCount(request.getGuestCount())
                .note(request.getNote())
                .createdBy(staff)
                .tables(new HashSet<>())
                .orders(new HashSet<>())
                .build();
        session = sessionRepository.save(session);

        // Gán table vào session
        table.setCurrentSession(session);
        table.setStatus(DiningTable.Status.OCCUPIED);
        tableRepository.save(table);
        session.getTables().add(table);

        // Tạo order mặc định
        Order order = Order.builder()
                .session(session)
                .status(Order.OrderStatus.OPEN)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(staff)
                .items(new HashSet<>())
                .build();
        order = orderRepository.save(order);
        session.getOrders().add(order);

        // Notify
        sendNotification("NEW_SESSION", table, "Bàn " + table.getName() + " vừa mở phiên mới");
        notifyTableUpdate();

        return session;
    }

    /**
     * Lấy session theo tableId (dùng khi quét QR).
     * Tự động tạo session mới nếu chưa có.
     */
    @Transactional
    public ServingSession getOrCreateByTable(Integer tableId) {
        SessionRequest.OpenSession request = new SessionRequest.OpenSession();
        request.setTableId(tableId);
        return openTable(request);
    }

    /**
     * Lấy session theo ID.
     */
    public ServingSession getSession(Long sessionId) {
        return sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));
    }

    // ==================== 2. THÊM MÓN ====================

    /**
     * Thêm món vào session.
     */
    @Transactional
    public void addItems(Long sessionId, SessionRequest.AddItems request) {
        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại hoặc đã kết thúc"));

        Order order = session.getPrimaryOrder();
        if (order == null) {
            throw new AppException(400, "Session không có order");
        }

        Employee staff = getCurrentStaff();
        DiningTable sourceTable = null;
        if (request.getSourceTableId() != null) {
            sourceTable = tableRepository.findById(request.getSourceTableId()).orElse(null);
        }

        for (AddItemRequest item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new AppException(404, "Món không tồn tại: " + item.getProductId()));

            if (product.getStatus() == Product.ProductStatus.OUT_OF_STOCK) {
                throw new AppException(400, "Món đã hết: " + product.getName());
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .originalTable(sourceTable)
                    .quantity(item.getQuantity())
                    .price(product.getPrice())
                    .note(item.getNote())
                    .status(OrderItem.ItemStatus.PENDING)
                    .createdBy(staff)
                    .build();
            orderItem = orderItemRepository.save(orderItem);

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            order.setTotalAmount(order.getTotalAmount().add(itemTotal));
            orderRepository.save(order);

            // Notify từng item đã thêm
            notifyItemEvent("ORDER_ITEM_ADDED", session, orderItem);
        }

        // Notify
        sendNotification("NEW_ITEM", session.getPrimaryTable(),
                "Bàn " + session.getTableNames() + " vừa gọi thêm món");
    }

    /**
     * Xóa món khỏi session.
     * Chỉ cho phép xóa món có status PENDING (chưa được ra món).
     */
    @Transactional
    public void removeItem(Long sessionId, Long itemId) {
        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại hoặc đã kết thúc"));

        Order order = session.getPrimaryOrder();
        if (order == null) {
            throw new AppException(400, "Session không có order");
        }

        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(404, "Món không tồn tại"));

        if (!item.getOrder().getId().equals(order.getId())) {
            throw new AppException(400, "Món không thuộc session này");
        }

        // Chỉ cho phép xóa món PENDING (chưa ra món)
        if (item.getStatus() != OrderItem.ItemStatus.PENDING) {
            throw new AppException(409, "Chỉ có thể xóa món chưa được ra. Món này đã được ra hoặc đã hủy.");
        }

        // Trừ tiền khỏi order
        BigDecimal itemTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        order.setTotalAmount(order.getTotalAmount().subtract(itemTotal));
        orderRepository.save(order);

        // Xóa item khỏi collection trong memory TRƯỚC KHI xóa từ DB
        // Điều này đảm bảo SessionResponse.fromEntity() không còn chứa item đã xóa
        order.getItems().remove(item);

        // Xóa item từ database
        orderItemRepository.delete(item);

        // Notify với event delete
        notifyDeleteEvent(session, itemId, order.getTotalAmount());
        sendNotification("REMOVE_ITEM", session.getPrimaryTable(),
                "Bàn " + session.getTableNames() + " vừa xóa món");
    }

    // ==================== 3. QUẢN LÝ BÀN TRONG SESSION (ATTACH / DETACH)
    // ====================

    /**
     * Attach Table (Thêm bàn vào session).
     * Thay thế cho Merge Table logic phức tạp cũ.
     */
    @Transactional
    public void attachTable(Long sessionId, Integer tableId) {
        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại hoặc đã kết thúc"));

        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new AppException(404, "Bàn không tồn tại"));

        if (!table.isAvailable()) {
            throw new AppException(400, "Bàn " + table.getName() + " đang có khách");
        }

        table.setCurrentSession(session);
        table.setStatus(DiningTable.Status.OCCUPIED);
        tableRepository.save(table);
        session.getTables().add(table);
        sessionRepository.save(session);

        notifySessionUpdate(session);
        notifyTableUpdate();
        sendNotification("ATTACH_TABLE", session.getPrimaryTable(),
                "Đã thêm bàn " + table.getName() + " vào session");
    }

    /**
     * Detach Table (Bỏ bàn khỏi session).
     * Cho phép khi session đang ACTIVE hoặc COMPLETED.
     * Ràng buộc: Session phải có ít nhất 1 bàn sau khi tách.
     */
    @Transactional
    public void detachTable(Long sessionId, Integer tableId) {
        ServingSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));

        // Chỉ cho phép tách bàn khi session ACTIVE hoặc COMPLETED
        if (session.getStatus() != ServingSession.SessionStatus.ACTIVE
                && session.getStatus() != ServingSession.SessionStatus.COMPLETED) {
            throw new AppException(400, "Không thể tách bàn khi session ở trạng thái " + session.getStatus());
        }

        // Kiểm tra session phải có ít nhất 1 bàn sau khi tách
        if (session.getTables().size() <= 1) {
            throw new AppException(400, "Session phải có ít nhất 1 bàn. Không thể tách bàn cuối cùng.");
        }

        DiningTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new AppException(404, "Bàn không tồn tại"));

        if (table.getCurrentSession() == null || !table.getCurrentSession().getId().equals(sessionId)) {
            throw new AppException(400, "Bàn không thuộc session này");
        }

        table.setCurrentSession(null);
        table.setStatus(DiningTable.Status.AVAILABLE);
        tableRepository.save(table);
        session.getTables().remove(table);
        sessionRepository.save(session);

        notifySessionUpdate(session);
        notifyTableUpdate();
        sendNotification("DETACH_TABLE", session.getPrimaryTable(),
                "Đã tách bàn " + table.getName() + " khỏi session");
    }

    /**
     * Cập nhật số lượng món trong session (US-14).
     * Chỉ cho phép cập nhật món có status PENDING.
     */
    @Transactional
    public void updateItemQuantity(Long sessionId, Long itemId, Integer newQuantity) {
        if (newQuantity == null || newQuantity < 1) {
            throw new AppException(400, "Số lượng phải >= 1. Nếu muốn xóa, dùng API xóa món.");
        }

        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại hoặc đã kết thúc"));

        Order order = session.getPrimaryOrder();
        if (order == null) {
            throw new AppException(400, "Session không có order");
        }

        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(404, "Món không tồn tại"));

        if (!item.getOrder().getId().equals(order.getId())) {
            throw new AppException(400, "Món không thuộc session này");
        }

        // Chỉ cho phép cập nhật món PENDING
        if (item.getStatus() != OrderItem.ItemStatus.PENDING) {
            throw new AppException(409, "Chỉ có thể cập nhật món chưa được ra. Món này đã được ra hoặc đã hủy.");
        }

        // Tính lại tổng tiền
        BigDecimal oldTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        BigDecimal newTotal = item.getPrice().multiply(BigDecimal.valueOf(newQuantity));
        BigDecimal diff = newTotal.subtract(oldTotal);

        item.setQuantity(newQuantity);
        orderItemRepository.save(item);

        order.setTotalAmount(order.getTotalAmount().add(diff));
        orderRepository.save(order);

        // Notify với event type cụ thể
        notifyItemEvent("ORDER_ITEM_UPDATED", session, item);
        sendNotification("UPDATE_ITEM", session.getPrimaryTable(),
                "Bàn " + session.getTableNames() + " vừa cập nhật số lượng món");
    }

    /**
     * Đánh dấu món đã mang ra (SERVE).
     * Chỉ cho phép với món có status PENDING.
     * 
     * @param sessionId ID của session
     * @param itemId    ID của order item
     * @throws AppException 404 nếu session/item không tồn tại
     * @throws AppException 409 nếu item không ở trạng thái PENDING
     */
    @Transactional
    public void serveItem(Long sessionId, Long itemId) {
        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại hoặc đã kết thúc"));

        Order order = session.getPrimaryOrder();
        if (order == null) {
            throw new AppException(400, "Session không có order");
        }

        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(404, "Món không tồn tại"));

        if (!item.getOrder().getId().equals(order.getId())) {
            throw new AppException(400, "Món không thuộc session này");
        }

        // Chỉ cho phép serve món PENDING
        if (item.getStatus() != OrderItem.ItemStatus.PENDING) {
            throw new AppException(409, "Món này đã được mang ra hoặc đã hủy.");
        }

        // Cập nhật trạng thái
        item.setStatus(OrderItem.ItemStatus.SERVED);
        orderItemRepository.save(item);

        // Notify với event type cụ thể
        notifyItemEvent("ORDER_ITEM_SERVED", session, item);
        sendNotification("SERVE_ITEM", session.getPrimaryTable(),
                "Bàn " + session.getTableNames() + ": Đã mang ra món " + item.getProduct().getName());
    }

    // ==================== [REMOVED] LEGACY OPERATIONS ====================
    // Các operations sau đã được LOẠI BỎ theo refactor Session-based thuần túy:
    // - mergeTables() -> Thay bằng attachTable()
    // - splitSession() -> KHÔNG HỖ TRỢ split bill. Chỉ cho phép detachTable()
    // - transferSession() -> Thay bằng sequence: attachTable(new) +
    // detachTable(old)
    //
    // Lý do:
    // - Đơn giản hóa nghiệp vụ
    // - Loại bỏ logic phức tạp không cần thiết cho quán F&B nhỏ-trung
    // - Tập trung vào 2 hành vi cốt lõi: Attach và Detach table

    // ==================== 6. THANH TOÁN ====================

    /**
     * Thanh toán và đóng session.
     */
    @Transactional
    public InvoiceDto paySession(Long sessionId, SessionRequest.PaySession request) {
        Employee cashier = getCurrentStaff();
        if (cashier == null) {
            throw new AppException(403, "Chỉ nhân viên mới được thực hiện thanh toán.");
        }

        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));

        // Đóng tất cả orders
        for (Order order : session.getOrders()) {
            if (order.getStatus() == Order.OrderStatus.OPEN) {
                order.setStatus(Order.OrderStatus.COMPLETED);
                order.setPaymentMethod(request.getMethod());
                order.setCompletedAt(LocalDateTime.now());
            }
        }
        orderRepository.saveAll(session.getOrders());

        // Release tất cả tables
        for (DiningTable table : session.getTables()) {
            table.setCurrentSession(null);
            table.setStatus(DiningTable.Status.AVAILABLE);
        }
        tableRepository.saveAll(session.getTables());

        // Đóng session
        session.setStatus(ServingSession.SessionStatus.COMPLETED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // Notify
        notifyTableUpdate();
        notifySessionUpdate(session);

        // Publish event
        Order primaryOrder = session.getPrimaryOrder();
        if (primaryOrder != null) {
            eventPublisher.publishEvent(new OrderPaidEvent(primaryOrder));
        }

        return createInvoice(session, cashier);
    }

    // ==================== 7. HỦY SESSION ====================

    /**
     * Hủy session.
     */
    @Transactional
    public void cancelSession(Long sessionId, SessionRequest.CancelSession request) {
        Employee staff = getCurrentStaff();
        if (staff == null) {
            throw new AppException(403, "Chỉ nhân viên mới có quyền hủy session.");
        }

        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));

        // Hủy tất cả orders
        for (Order order : session.getOrders()) {
            order.setStatus(Order.OrderStatus.CANCELLED);
        }
        orderRepository.saveAll(session.getOrders());

        // Release tất cả tables
        for (DiningTable table : session.getTables()) {
            table.setCurrentSession(null);
            table.setStatus(DiningTable.Status.AVAILABLE);
        }
        tableRepository.saveAll(session.getTables());

        // Hủy session
        session.setStatus(ServingSession.SessionStatus.CANCELLED);
        session.setEndedAt(LocalDateTime.now());
        session.setNote((session.getNote() != null ? session.getNote() + " | " : "")
                + "Hủy: " + (request.getReason() != null ? request.getReason() : "Không có lý do"));
        sessionRepository.save(session);

        notifyTableUpdate();
        notifySessionUpdate(session);
    }

    // ==================== 8. CUSTOMER ORDER (QR) ====================

    /**
     * Khách đặt món qua QR - tạo pending session hoặc thêm vào session hiện có.
     */
    @Transactional
    public CustomerOrderResponse createCustomerOrder(CustomerOrderRequest request) {
        DiningTable table = tableRepository.findById(request.getTableId())
                .orElseThrow(() -> new AppException(404, "Bàn không tồn tại"));

        ServingSession session;

        // Kiểm tra trạng thái bàn
        if (table.getCurrentSession() != null) {
            ServingSession currentSession = table.getCurrentSession();

            if (currentSession.getStatus() == ServingSession.SessionStatus.PENDING) {
                throw new AppException(400, "Bàn này đang có order chờ xác nhận. Vui lòng đợi nhân viên xử lý.");
            }

            if (currentSession.getStatus() == ServingSession.SessionStatus.ACTIVE) {
                // Bàn đang có session ACTIVE → Thêm món vào session hiện tại
                return addCustomerItems(currentSession.getId(), request);
            }
        }

        if (table.getStatus() == DiningTable.Status.RESERVED) {
            throw new AppException(400, "Bàn này đã được đặt trước.");
        }

        // Tạo pending session mới
        session = ServingSession.builder()
                .status(ServingSession.SessionStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .note(request.getCustomerNote())
                .createdBy(null) // Customer tạo, không có employee
                .tables(new HashSet<>())
                .orders(new HashSet<>())
                .build();
        session = sessionRepository.save(session);

        // Gán table vào session (nhưng chưa chuyển OCCUPIED)
        table.setCurrentSession(session);
        // Table vẫn giữ status AVAILABLE cho đến khi confirm
        tableRepository.save(table);
        session.getTables().add(table);

        // Tạo order với items
        Order order = Order.builder()
                .session(session)
                .status(Order.OrderStatus.OPEN)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(null)
                .items(new HashSet<>())
                .build();
        order = orderRepository.save(order);
        session.getOrders().add(order);

        // Thêm các món
        addItemsToOrder(order, request.getItems(), table);

        // Notify nhân viên có order mới
        sendNotification("CUSTOMER_ORDER", table,
                "🔔 Bàn " + table.getName() + " có order mới từ khách!");
        notifyPendingSessionUpdate();

        return buildCustomerOrderResponse(session);
    }

    /**
     * Khách thêm món vào session đang active.
     */
    @Transactional
    public CustomerOrderResponse addCustomerItems(Long sessionId, CustomerOrderRequest request) {
        ServingSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));

        if (session.getStatus() != ServingSession.SessionStatus.ACTIVE) {
            throw new AppException(400, "Chỉ có thể thêm món khi order đã được xác nhận.");
        }

        Order order = session.getPrimaryOrder();
        DiningTable table = session.getPrimaryTable();

        addItemsToOrder(order, request.getItems(), table);

        // Notify nhân viên có thêm món
        sendNotification("NEW_ITEM", table,
                "Bàn " + session.getTableNames() + " vừa gọi thêm món (từ khách)");
        notifySessionUpdate(session);

        return buildCustomerOrderResponse(session);
    }

    /**
     * Khách kiểm tra trạng thái order.
     */
    public CustomerOrderResponse getCustomerOrderStatus(Long sessionId) {
        ServingSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Order không tồn tại"));
        return buildCustomerOrderResponse(session);
    }

    /**
     * Lấy danh sách pending sessions (cho nhân viên).
     */
    public List<SessionResponse> getPendingSessions() {
        return sessionRepository.findPendingSessions().stream()
                .map(SessionResponse::fromEntity)
                .toList();
    }

    /**
     * Lấy danh sách active sessions (cho nhân viên).
     */
    public List<SessionResponse> getActiveSessions() {
        return sessionRepository.findActiveSessions().stream()
                .map(SessionResponse::fromEntity)
                .toList();
    }

    /**
     * Nhân viên xác nhận session (PENDING → ACTIVE).
     */
    @Transactional
    public ServingSession confirmSession(Long sessionId) {
        ServingSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));

        if (session.getStatus() != ServingSession.SessionStatus.PENDING) {
            throw new AppException(400, "Session này không ở trạng thái chờ xác nhận.");
        }

        Employee staff = getCurrentStaff();

        // Chuyển session sang ACTIVE
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        session.setCreatedBy(staff);
        sessionRepository.save(session);

        // Chuyển table sang OCCUPIED
        for (DiningTable table : session.getTables()) {
            table.setStatus(DiningTable.Status.OCCUPIED);
            tableRepository.save(table);
        }

        // Notify
        notifyTableUpdate();
        notifySessionUpdate(session);
        notifyPendingSessionUpdate();
        sendNotification("SESSION_CONFIRMED", session.getPrimaryTable(),
                "✅ Order bàn " + session.getTableNames() + " đã được xác nhận");

        return session;
    }

    /**
     * Nhân viên từ chối session (PENDING → CANCELLED).
     */
    @Transactional
    public void rejectSession(Long sessionId, String reason) {
        ServingSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));

        if (session.getStatus() != ServingSession.SessionStatus.PENDING) {
            throw new AppException(400, "Session này không ở trạng thái chờ xác nhận.");
        }

        // Hủy session
        session.setStatus(ServingSession.SessionStatus.CANCELLED);
        session.setEndedAt(LocalDateTime.now());
        session.setNote((session.getNote() != null ? session.getNote() + " | " : "")
                + "Từ chối: " + (reason != null ? reason : "Không có lý do"));
        sessionRepository.save(session);

        // Hủy orders
        for (Order order : session.getOrders()) {
            order.setStatus(Order.OrderStatus.CANCELLED);
        }
        orderRepository.saveAll(session.getOrders());

        // Release table
        for (DiningTable table : session.getTables()) {
            table.setCurrentSession(null);
            table.setStatus(DiningTable.Status.AVAILABLE);
        }
        tableRepository.saveAll(session.getTables());

        // Notify
        notifyTableUpdate();
        notifySessionUpdate(session);
        notifyPendingSessionUpdate();
        sendNotification("SESSION_REJECTED", session.getPrimaryTable(),
                "❌ Order bàn " + session.getTableNames() + " đã bị từ chối");
    }

    // ==================== PRIVATE HELPERS ====================

    private void addItemsToOrder(Order order, List<AddItemRequest> items, DiningTable sourceTable) {
        for (AddItemRequest item : items) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new AppException(404, "Món không tồn tại: " + item.getProductId()));

            if (product.getStatus() == Product.ProductStatus.OUT_OF_STOCK) {
                throw new AppException(400, "Món đã hết: " + product.getName());
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .originalTable(sourceTable)
                    .quantity(item.getQuantity())
                    .price(product.getPrice())
                    .note(item.getNote())
                    .status(OrderItem.ItemStatus.PENDING)
                    .createdBy(null) // Customer order
                    .build();
            orderItemRepository.save(orderItem);

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            order.setTotalAmount(order.getTotalAmount().add(itemTotal));
        }
        orderRepository.save(order);
    }

    private CustomerOrderResponse buildCustomerOrderResponse(ServingSession session) {
        Order order = session.getPrimaryOrder();
        DiningTable table = session.getPrimaryTable();

        List<CustomerOrderResponse.OrderItemDto> items = order.getItems().stream()
                .map(i -> CustomerOrderResponse.OrderItemDto.builder()
                        .id(i.getId())
                        .productName(i.getProduct().getName())
                        .productImage(getProductFirstImage(i.getProduct()))
                        .quantity(i.getQuantity())
                        .price(i.getPrice())
                        .total(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .note(i.getNote())
                        .build())
                .toList();

        String rejectReason = null;
        if (session.getStatus() == ServingSession.SessionStatus.CANCELLED && session.getNote() != null) {
            // Extract reject reason from note
            if (session.getNote().contains("Từ chối:")) {
                rejectReason = session.getNote().substring(session.getNote().lastIndexOf("Từ chối:") + 9).trim();
            }
        }

        return CustomerOrderResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .statusMessage(CustomerOrderResponse.getStatusMessage(session.getStatus()))
                .tableId(table != null ? table.getId() : null)
                .tableName(table != null ? table.getName() : null)
                .createdAt(session.getStartedAt())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .rejectReason(rejectReason)
                .build();
    }

    private String getProductFirstImage(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }
        return product.getImages().get(0).getImageUrl();
    }

    private void notifyPendingSessionUpdate() {
        String tenantId = TenantContext.getTenantId();
        try {
            String topic = "/topic/tenant/" + tenantId + "/pending-sessions";
            List<SessionResponse> pendingSessions = getPendingSessions();
            messagingTemplate.convertAndSend(topic, pendingSessions);
        } catch (Exception e) {
            log.error("Socket update error", e);
        }
    }

    // ==================== HELPERS ====================

    private InvoiceDto createInvoice(ServingSession session, Employee cashier) {
        String tenantId = TenantContext.getTenantId();
        var tenant = tenantRepository.findById(tenantId).orElseThrow();

        Order order = session.getPrimaryOrder();
        List<InvoiceDto.InvoiceItemDto> items = order.getItems().stream()
                .map(i -> InvoiceDto.InvoiceItemDto.builder()
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .price(i.getPrice())
                        .total(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .build())
                .toList();

        BigDecimal total = session.getOrders().stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return InvoiceDto.builder()
                .tenantName(tenant.getName())
                .tenantAddress(tenant.getAddress())
                .tenantLogo(tenant.getLogoUrl())
                .orderId(order.getId())
                .tableName(session.getTableNames())
                .checkInTime(session.getStartedAt())
                .checkOutTime(session.getEndedAt())
                .cashierName(cashier.getUser().getFullName())
                .items(items)
                .totalAmount(total)
                .paymentMethod(order.getPaymentMethod())
                .build();
    }

    private void notifySessionUpdate(ServingSession session) {
        String tenantId = TenantContext.getTenantId();
        try {
            // Notify tất cả tables trong session
            for (DiningTable table : session.getTables()) {
                String topic = "/topic/tenant/" + tenantId + "/table/" + table.getId();
                SessionResponse payload = SessionResponse.fromEntity(session);
                messagingTemplate.convertAndSend(topic, payload);
            }
            // Notify session topic
            String sessionTopic = "/topic/tenant/" + tenantId + "/session/" + session.getId();
            SessionResponse payload = SessionResponse.fromEntity(session);
            messagingTemplate.convertAndSend(sessionTopic, payload);
        } catch (Exception e) {
            log.error("Socket update error", e);
        }
    }

    /**
     * Notify item event (ADD, UPDATE, SERVE).
     */
    private void notifyItemEvent(String eventType, ServingSession session, OrderItem item) {
        String tenantId = TenantContext.getTenantId();
        BigDecimal sessionTotal = session.getOrders().stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SessionEvent event = SessionEvent.createItemEvent(eventType, session.getId(), item, sessionTotal);
        // Thêm toàn bộ session data để tránh race condition ở frontend
        event.setSessionData(SessionResponse.fromEntity(session));

        try {
            // Notify tất cả tables trong session
            for (DiningTable table : session.getTables()) {
                String topic = "/topic/tenant/" + tenantId + "/table/" + table.getId();
                messagingTemplate.convertAndSend(topic, event);
            }
            // Notify session topic
            String sessionTopic = "/topic/tenant/" + tenantId + "/session/" + session.getId();
            messagingTemplate.convertAndSend(sessionTopic, event);
        } catch (Exception e) {
            log.error("Socket update error", e);
        }
    }

    /**
     * Notify delete event.
     */
    private void notifyDeleteEvent(ServingSession session, Long itemId, BigDecimal newTotal) {
        String tenantId = TenantContext.getTenantId();
        SessionEvent event = SessionEvent.createDeleteEvent(session.getId(), itemId, newTotal);
        // Thêm toàn bộ session data để tránh race condition ở frontend
        event.setSessionData(SessionResponse.fromEntity(session));

        try {
            // Notify tất cả tables trong session
            for (DiningTable table : session.getTables()) {
                String topic = "/topic/tenant/" + tenantId + "/table/" + table.getId();
                messagingTemplate.convertAndSend(topic, event);
            }
            // Notify session topic
            String sessionTopic = "/topic/tenant/" + tenantId + "/session/" + session.getId();
            messagingTemplate.convertAndSend(sessionTopic, event);
        } catch (Exception e) {
            log.error("Socket update error", e);
        }
    }

    private void notifyTableUpdate() {
        String tenantId = TenantContext.getTenantId();
        try {
            String topic = "/topic/tenant/" + tenantId + "/tables";
            List<TableDto> tables = tableRepository.findAll().stream()
                    .map(t -> TableDto.builder()
                            .id(t.getId())
                            .name(t.getName())
                            .status(t.getStatus())
                            .sessionId(t.getCurrentSession() != null ? t.getCurrentSession().getId() : null)
                            .qrCodeUrl(t.getQrCodeUrl())
                            .build())
                    .toList();
            messagingTemplate.convertAndSend(topic, tables);
        } catch (Exception e) {
            log.error("Socket update error", e);
        }
    }

    private void sendNotification(String type, DiningTable table, String content) {
        String tenantId = TenantContext.getTenantId();
        String topic = "/topic/tenant/" + tenantId + "/notifications";

        NotificationMessage msg = NotificationMessage.builder()
                .type(type)
                .title("Thông báo mới")
                .content(content)
                .tableId(table != null ? table.getId() : null)
                .tableName(table != null ? table.getName() : null)
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
}
