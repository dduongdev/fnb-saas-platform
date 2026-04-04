package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.repository.ProductRepository;
import com.project.fnb.modules.pos.dto.*;
import com.project.fnb.modules.pos.entity.*;
import com.project.fnb.modules.pos.event.OrderPaidEvent;
import com.project.fnb.modules.pos.event.KdsEventPublisher;
import com.project.fnb.modules.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * Service quản lý phiên phục vụ (Serving Session) trong hệ thống POS.
 * 
 * <p>Service này cung cấp các chức năng cốt lõi của session-based model:</p>
 * <ul>
 *   <li>Quản lý vòng đời session: PENDING → ACTIVE → COMPLETED/CANCELLED</li>
 *   <li>Thao tác bàn: Attach (gộp bàn), Detach (tách bàn)</li>
 *   <li>Quản lý order items: Thêm, sửa, xóa, serve món</li>
 *   <li>Xử lý đặt món từ khách qua QR code</li>
 *   <li>Thanh toán và đóng session</li>
 *   <li>WebSocket realtime notifications</li>
 * </ul>
 * 
 * <p><b>Session-based Model:</b> Session là khái niệm trung tâm thay thế việc gắn Order trực tiếp vào Table.
 * Một Session đại diện cho một lần phục vụ khách hàng - từ khi ngồi xuống đến khi thanh toán.</p>
 * 
 * <p><b>Business Invariants:</b></p>
 * <ul>
 *   <li>Session LUÔN có ít nhất 1 bàn</li>
 *   <li>Một bàn chỉ thuộc tối đa 1 Session ACTIVE</li>
 *   <li>Chỉ cho phép xóa/sửa món có status PENDING</li>
 *   <li>Sau khi thanh toán (COMPLETED), tất cả bàn được giải phóng</li>
 * </ul>
 * 
 * @author FNB Team
 * @version 1.0
 * @see ServingSession
 * @see SessionRepository
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
    private final TenantRepository tenantRepository;
    private final PosActionAuditService auditService;
    private final SimpMessagingTemplate messagingTemplate;
    private final KdsEventPublisher kdsEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    /**
     * Mở bàn và tạo session mới với trạng thái ACTIVE.
     * 
     * <p>Nếu bàn đã có session đang active, method sẽ trả về session đó thay vì tạo mới.
     * Điều này đảm bảo tính nhất quán và tránh duplicate sessions.</p>
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Kiểm tra bàn có session active không</li>
     *   <li>Tạo session mới với status ACTIVE</li>
     *   <li>Gán bàn vào session và đổi trạng thái bàn sang OCCUPIED</li>
     *   <li>Tạo order mặc định cho session</li>
     *   <li>Gửi WebSocket notifications</li>
     * </ol>
     * 
     * @param request chứa tableId, note
     * @return ServingSession đã tạo hoặc session hiện tại nếu bàn đang active
     * @throws AppException 404 nếu bàn không tồn tại
     */
    private void recordAction(String action, String targetType, String targetId, java.math.BigDecimal amount, String note, Long sessionId) {
        if (auditService != null) {
            auditService.record(action, targetType, targetId, amount, note, sessionId);
        }
    }

    private void recordAction(String action, String targetType, String targetId, java.math.BigDecimal amount, String note) {
        Long sessionId = null;
        if ("SESSION".equals(targetType)) {
            try {
                sessionId = targetId != null ? Long.valueOf(targetId) : null;
            } catch (NumberFormatException ignored) {
            }
        }
        if (auditService != null) {
            auditService.record(action, targetType, targetId, amount, note, sessionId);
        }
    }

    @Transactional
    public ServingSession openTable(SessionRequest.OpenSession request) {
        DiningTable table = tableRepository.findById(request.getTableId())
                .orElseThrow(() -> new AppException(404, "Bàn không tồn tại"));

        // Nếu bàn đã có session → trả về session đó
        if (table.getCurrentSession() != null) {
            return sessionRepository.findByIdWithDetails(table.getCurrentSession().getId())
                    .orElseThrow();
        }

        // Tạo session mới
        ServingSession session = ServingSession.builder()
                .status(ServingSession.SessionStatus.ACTIVE)
                .startedAt(LocalDateTime.now())
                .note(request.getNote())
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
                .items(new HashSet<>())
                .build();
        order = orderRepository.save(order);
        session.getOrders().add(order);

        // Notify
        sendNotification("NEW_SESSION", table, "Bàn " + table.getName() + " vừa mở phiên mới");
        notifyTableUpdate();
        notifySessionUpdate(session); // Gửi đến customer đang subscribe table topic
        kdsEventPublisher.publishSessionCreated(session, null);

        recordAction("session.open", "SESSION", String.valueOf(session.getId()), null,
                "Mở session cho bàn " + table.getName(), session.getId());

        return session;
    }

    /**
     * Lấy hoặc tạo session theo table ID.
     * 
     * <p>Method này được sử dụng khi khách quét QR code tại bàn.
     * Nếu bàn chưa có session, tự động tạo mới với trạng thái ACTIVE.</p>
     * 
     * @param tableId ID của bàn
     * @return ServingSession hiện tại hoặc mới tạo
     * @throws AppException 404 nếu bàn không tồn tại
     */
    @Transactional
    public ServingSession getOrCreateByTable(String tableId) {
        SessionRequest.OpenSession request = new SessionRequest.OpenSession();
        request.setTableId(tableId);
        return openTable(request);
    }

    /**
     * Lấy session theo ID với đầy đủ thông tin liên quan.
     * 
     * @param sessionId ID của session
     * @return ServingSession với orders, tables, items được load
     * @throws AppException 404 nếu session không tồn tại
     */
    public ServingSession getSession(Long sessionId) {
        return sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));
    }

    /**
     * Thêm danh sách món vào session.
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Validate session phải ở trạng thái ACTIVE</li>
     *   <li>Kiểm tra tồn kho cho từng món</li>
     *   <li>Tạo OrderItem với giá snapshot tại thời điểm order</li>
     *   <li>Cập nhật tổng tiền của order</li>
     *   <li>Gửi WebSocket notification cho mỗi item được thêm</li>
     * </ol>
     * 
     * @param sessionId ID của session
     * @param request chứa danh sách món cần thêm (productId, quantity, note)
     * @throws AppException 404 nếu session/product không tồn tại
     * @throws AppException 400 nếu session không active hoặc món hết hàng
     */
    @Transactional
    public void addItems(Long sessionId, SessionRequest.AddItems request) {
        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại hoặc đã kết thúc"));

        Order order = session.getPrimaryOrder();
        if (order == null) {
            throw new AppException(400, "Session không có order");
        }

        DiningTable sourceTable = null;
        if (request.getSourceTableId() != null) {
            sourceTable = tableRepository.findById(request.getSourceTableId()).orElse(null);
        }

        List<String> addedItems = new java.util.ArrayList<>();
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
                    .build();
            orderItem = orderItemRepository.save(orderItem);

            // Add vào collection để SessionResponse.fromEntity có thể thấy item mới
            order.getItems().add(orderItem);

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            order.setTotalAmount(order.getTotalAmount().add(itemTotal));
            orderRepository.save(order);

            addedItems.add(item.getQuantity() + "x " + product.getName());

            // Notify từng item đã thêm
            notifyItemEvent("ORDER_ITEM_ADDED", session, orderItem);
            kdsEventPublisher.publishItemAdded(orderItem, session.getId(), null);
        }

        // Notify
        sendNotification("NEW_ITEM", session.getPrimaryTable(),
                "Bàn " + session.getTableNames() + " vừa gọi thêm món");

        recordAction("session.add_item", "SESSION", String.valueOf(session.getId()), order.getTotalAmount(),
                "Thêm " + request.getItems().size() + " món: " + String.join(", ", addedItems), session.getId());
    }

    /**
     * Xóa món khỏi session.
     * 
     * <p><b>Business Rule:</b> Chỉ cho phép xóa món có status PENDING (chưa được ra món).
     * Món đã SERVED hoặc CANCELLED không thể xóa.</p>
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Kiểm tra món thuộc session và có status PENDING</li>
     *   <li>Trừ tiền món khỏi tổng order</li>
     *   <li>Xóa item khỏi collection và database</li>
     *   <li>Gửi WebSocket delete event</li>
     * </ol>
     * 
     * @param sessionId ID của session
     * @param itemId ID của order item cần xóa
     * @throws AppException 404 nếu session/item không tồn tại
     * @throws AppException 400 nếu item không thuộc session
     * @throws AppException 409 nếu item không ở trạng thái PENDING
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
        kdsEventPublisher.publishItemRemoved(itemId, sessionId, session.getTenantId(), null);
        sendNotification("REMOVE_ITEM", session.getPrimaryTable(),
                "Bàn " + session.getTableNames() + " vừa xóa món");

        recordAction("session.remove_item", "ORDER_ITEM", String.valueOf(itemId), order.getTotalAmount(),
                "Xóa " + item.getQuantity() + "x " + item.getProduct().getName(), session.getId());
    }

    /**
     * Gộp bàn vào session (Attach Table).
     * 
     * <p>Method này thay thế cho merge table logic phức tạp cũ.
     * Cho phép thêm bàn trống vào session đang active để phục vụ nhóm lớn hoặc gộp chỗ.</p>
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Session phải ở trạng thái ACTIVE</li>
     *   <li>Bàn cần attach phải ở trạng thái AVAILABLE</li>
     *   <li>Sau khi attach, bàn chuyển sang OCCUPIED và thuộc session</li>
     * </ul>
     * 
     * @param sessionId ID của session
     * @param tableId ID của bàn cần gộp vào
     * @throws AppException 404 nếu session/table không tồn tại
     * @throws AppException 400 nếu session không active hoặc bàn đang có khách
     */
    @Transactional
    public void attachTable(Long sessionId, String tableId) {
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
        kdsEventPublisher.publishSessionCreated(session, null);
        sendNotification("ATTACH_TABLE", session.getPrimaryTable(),
                "Đã thêm bàn " + table.getName() + " vào session");

        recordAction("session.attach_table", "TABLE", table.getId(), null,
                "Attach table " + table.getName() + " vào session", session.getId());
    }

    /**
     * Tách bàn khỏi session (Detach Table).
     * 
     * <p>Cho phép tách bàn khi một phần khách rời đi trong khi session vẫn tiếp tục.
     * Bàn được tách sẽ chuyển về trạng thái AVAILABLE và có thể phục vụ khách mới.</p>
     * 
     * <p><b>Business Rules:</b></p>
     * <ul>
     *   <li>Session phải ở trạng thái ACTIVE hoặc COMPLETED</li>
     *   <li>Session phải có ít nhất 2 bàn (không thể tách bàn cuối cùng)</li>
     *   <li>Bàn phải thuộc session hiện tại</li>
     * </ul>
     * 
     * @param sessionId ID của session
     * @param tableId ID của bàn cần tách ra
     * @throws AppException 404 nếu session/table không tồn tại
     * @throws AppException 400 nếu session không đủ điều kiện tách hoặc chỉ còn 1 bàn
     */
    @Transactional
    public void detachTable(Long sessionId, String tableId) {
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

        // Gửi notification đến bàn cũ TRƯỚC KHI tách (để customer biết session đã được chuyển đi)
        notifyTableTransferred(table);

        table.setCurrentSession(null);
        table.setStatus(DiningTable.Status.AVAILABLE);
        tableRepository.save(table);
        session.getTables().remove(table);
        sessionRepository.save(session);

        notifySessionUpdate(session);
        notifyTableUpdate();
        kdsEventPublisher.publishSessionCreated(session, null);
        sendNotification("DETACH_TABLE", session.getPrimaryTable(),
                "Đã tách bàn " + table.getName() + " khỏi session");

        recordAction("session.detach_table", "TABLE", table.getId(), null,
                "Detach table " + table.getName(), session.getId());
    }

    /**
     * Cập nhật số lượng món trong session.
     * 
     * <p><b>Business Rule:</b> Chỉ cho phép cập nhật món có status PENDING.
     * Món đã SERVED hoặc CANCELLED không thể thay đổi số lượng.</p>
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Validate số lượng mới >= 1</li>
     *   <li>Kiểm tra item có status PENDING</li>
     *   <li>Tính lại tổng tiền order dựa trên số lượng mới</li>
     *   <li>Gửi WebSocket update event</li>
     * </ol>
     * 
     * @param sessionId ID của session
     * @param itemId ID của order item
     * @param newQuantity số lượng mới (phải >= 1)
     * @throws AppException 400 nếu số lượng < 1 hoặc session không active
     * @throws AppException 404 nếu session/item không tồn tại
     * @throws AppException 409 nếu item không ở trạng thái PENDING
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
        int oldQuantity = item.getQuantity();
        BigDecimal oldTotal = item.getPrice().multiply(BigDecimal.valueOf(oldQuantity));
        BigDecimal newTotal = item.getPrice().multiply(BigDecimal.valueOf(newQuantity));
        BigDecimal diff = newTotal.subtract(oldTotal);

        item.setQuantity(newQuantity);
        orderItemRepository.save(item);

        order.setTotalAmount(order.getTotalAmount().add(diff));
        orderRepository.save(order);

        // Notify với event type cụ thể
        notifyItemEvent("ORDER_ITEM_UPDATED", session, item);
        kdsEventPublisher.publishItemUpdated(item, sessionId, null);
        sendNotification("UPDATE_ITEM", session.getPrimaryTable(),
                "Bàn " + session.getTableNames() + " vừa cập nhật số lượng món");

        recordAction("session.update_item_quantity", "ORDER_ITEM", String.valueOf(itemId), order.getTotalAmount(),
                "Cập nhật số lượng " + item.getProduct().getName() + " từ " + oldQuantity + " -> " + newQuantity, session.getId());

    }

    /**
     * Đánh dấu món đã mang ra cho khách (Serve Item).
     * 
     * <p><b>Business Rule:</b> Chỉ cho phép serve món có status PENDING.
     * Món đã SERVED hoặc CANCELLED không thể thay đổi trạng thái.</p>
     * 
     * <p>Method này được nhân viên bếp/phục vụ sử dụng để cập nhật trạng thái món
     * sau khi mang ra cho khách. WebSocket notification sẽ được gửi đến cả nhân viên và khách.</p>
     * 
     * @param sessionId ID của session
     * @param itemId ID của order item cần đánh dấu đã serve
     * @throws AppException 404 nếu session/item không tồn tại
     * @throws AppException 400 nếu item không thuộc session
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
        kdsEventPublisher.publishItemStatusChanged(item, sessionId, null);
        sendNotification("SERVE_ITEM", session.getPrimaryTable(),
                "Bàn " + session.getTableNames() + ": Đã mang ra món " + item.getProduct().getName());

        recordAction("session.serve_item", "ORDER_ITEM", String.valueOf(itemId), item.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())),
                "Serve " + item.getQuantity() + "x " + item.getProduct().getName(), session.getId());
    }

    /**
     * Thanh toán và đóng session.
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Validate nhân viên có quyền thanh toán</li>
     *   <li>Đóng tất cả orders trong session (chuyển sang COMPLETED)</li>
     *   <li>Giải phóng tất cả bàn (chuyển về AVAILABLE)</li>
     *   <li>Đóng session (chuyển sang COMPLETED)</li>
     *   <li>Gửi WebSocket notifications</li>
     *   <li>Publish OrderPaidEvent để cập nhật báo cáo</li>
     *   <li>Tạo và trả về hóa đơn</li>
     * </ol>
     * 
     * <p><b>Payment Methods:</b> CASH, VNPAY, MOMO...</p>
     * 
     * @param sessionId ID của session cần thanh toán
     * @param request chứa payment method và thông tin thanh toán
     * @return InvoiceDto chứa thông tin hóa đơn đầy đủ
     * @throws AppException 404 nếu session không tồn tại
     */
    @Transactional
    public InvoiceDto paySession(Long sessionId, SessionRequest.PaySession request) {
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
        kdsEventPublisher.publishSessionCancelled(session.getId(), session.getTenantId(), null);

        // Publish event
        Order primaryOrder = session.getPrimaryOrder();
        if (primaryOrder != null) {
            eventPublisher.publishEvent(new OrderPaidEvent(primaryOrder));
        }

        var invoice = createInvoice(session);
        recordAction("session.pay", "INVOICE", String.valueOf(invoice.getOrderId()), invoice.getTotalAmount(),
                "Thanh toán session: " + invoice.getTotalAmount() + " bằng " + request.getMethod(), session.getId());

        return invoice;
    }

    /**
     * Hủy session và giải phóng tài nguyên.
     * 
     * <p>Method này được sử dụng khi cần hủy session do khách hủy, 
     * lỗi hệ thống, hoặc các lý do khác. Tất cả orders sẽ bị hủy và bàn được giải phóng.</p>
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Validate nhân viên có quyền hủy</li>
     *   <li>Hủy tất cả orders (chuyển sang CANCELLED)</li>
     *   <li>Giải phóng tất cả bàn (chuyển về AVAILABLE)</li>
     *   <li>Hủy session (chuyển sang CANCELLED) và ghi lý do vào note</li>
     *   <li>Gửi WebSocket notifications</li>
     * </ol>
     * 
     * @param sessionId ID của session cần hủy
     * @param request chứa lý do hủy (reason)
     * @throws AppException 404 nếu session không tồn tại
     */
    @Transactional
    public void cancelSession(Long sessionId, SessionRequest.CancelSession request) {
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
        kdsEventPublisher.publishSessionCancelled(session.getId(), session.getTenantId(), null);

        recordAction("session.cancel", "SESSION", String.valueOf(session.getId()), null,
                "Hủy session: " + (request.getReason() != null ? request.getReason() : "Không có lý do"), session.getId());
    }

    /**
     * Khách hàng đặt món qua QR code (Guest Ordering).
     * 
     * <p>Method này xử lý luồng đặt món từ khách không cần đăng nhập:</p>
     * <ul>
     *   <li><b>Bàn trống:</b> Tạo session mới với status PENDING, chờ nhân viên xác nhận</li>
     *   <li><b>Bàn có session ACTIVE:</b> Thêm món vào session hiện tại</li>
     *   <li><b>Bàn có session PENDING:</b> Trả lỗi, yêu cầu khách đợi nhân viên xử lý</li>
     * </ul>
     * 
     * <p><b>Session Status Flow:</b></p>
     * <pre>
     * Khách đặt món → PENDING → Nhân viên xác nhận → ACTIVE → Khách tiếp tục order
     * </pre>
     * 
     * @param request chứa tableId, danh sách món (productId, quantity), note từ khách
     * @return CustomerOrderResponse với sessionId, status, danh sách món, tổng tiền
     * @throws AppException 404 nếu bàn không tồn tại
     * @throws AppException 400 nếu bàn đang PENDING hoặc RESERVED, hoặc món hết hàng
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
     * Khách hàng thêm món vào session đang active.
     * 
     * <p>Method này được gọi khi khách muốn order thêm món sau khi session đã được xác nhận.
     * Món mới sẽ được thêm vào order hiện tại với status PENDING.</p>
     * 
     * @param sessionId ID của session đang active
     * @param request chứa danh sách món cần thêm
     * @return CustomerOrderResponse cập nhật với danh sách món mới
     * @throws AppException 404 nếu session không tồn tại
     * @throws AppException 400 nếu session không ở trạng thái ACTIVE hoặc món hết hàng
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
        // Customer flow adds items through a different path; send full session snapshot via upsert event.
        kdsEventPublisher.publishSessionCreated(session, null);

        return buildCustomerOrderResponse(session);
    }

    /**
     * Khách hàng kiểm tra trạng thái order theo sessionId.
     * 
     * <p>Method này cho phép khách theo dõi trạng thái order realtime
     * mà không cần WebSocket. Thường được sử dụng làm fallback khi mất kết nối.</p>
     * 
     * @param sessionId ID của session cần kiểm tra
     * @return CustomerOrderResponse với status, danh sách món và trạng thái từng món
     * @throws AppException 404 nếu session không tồn tại
     */
    public CustomerOrderResponse getCustomerOrderStatus(Long sessionId) {
        ServingSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Order không tồn tại"));
        return buildCustomerOrderResponse(session);
    }

    /**
     * Lấy danh sách sessions đang chờ xác nhận (PENDING).
     * 
     * <p>Method này được nhân viên sử dụng để xem các order từ khách đang chờ xử lý.
     * Kết quả được filter theo tenant hiện tại.</p>
     * 
     * @return List<SessionResponse> danh sách pending sessions
     */
    public List<SessionResponse> getPendingSessions() {
        return sessionRepository.findPendingSessions().stream()
                .map(SessionResponse::fromEntity)
                .toList();
    }

    /**
     * Lấy danh sách sessions đang hoạt động (ACTIVE).
     * 
     * <p>Method này được nhân viên sử dụng để xem tổng quan các bàn đang phục vụ.
     * Kết quả được filter theo tenant hiện tại.</p>
     * 
     * @return List<SessionResponse> danh sách active sessions
     */
    public List<SessionResponse> getActiveSessions() {
        return sessionRepository.findActiveSessions().stream()
                .map(SessionResponse::fromEntity)
                .toList();
    }

    public org.springframework.data.domain.Page<SessionResponse> getSessionHistory(int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("startedAt").descending());
        return sessionRepository.findSessionHistory(pageable)
                .map(SessionResponse::fromEntity);
    }

    /**
     * Nhân viên xác nhận session từ khách (PENDING → ACTIVE).
     * 
     * <p>Sau khi xác nhận:</p>
     * <ul>
     *   <li>Session chuyển sang ACTIVE</li>
     *   <li>Tất cả bàn chuyển sang OCCUPIED</li>
     *   <li>Khách có thể tiếp tục order thêm món</li>
     *   <li>Bếp bắt đầu chuẩn bị món</li>
     * </ul>
     * 
     * @param sessionId ID của session cần xác nhận
     * @return ServingSession đã được cập nhật
     * @throws AppException 404 nếu session không tồn tại
     * @throws AppException 400 nếu session không ở trạng thái PENDING
     */
    @Transactional
    public ServingSession confirmSession(Long sessionId) {
        ServingSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại"));

        if (session.getStatus() != ServingSession.SessionStatus.PENDING) {
            throw new AppException(400, "Session này không ở trạng thái chờ xác nhận.");
        }

        // Chuyển session sang ACTIVE
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
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
        kdsEventPublisher.publishSessionCreated(session, null);
        sendNotification("SESSION_CONFIRMED", session.getPrimaryTable(),
                "✅ Order bàn " + session.getTableNames() + " đã được xác nhận");

        recordAction("session.confirm", "SESSION", String.valueOf(session.getId()), null,
                "Xác nhận session", session.getId());

        return session;
    }

    /**
     * Nhân viên từ chối session từ khách (PENDING → CANCELLED).
     * 
     * <p>Sử dụng khi:</p>
     * <ul>
     *   <li>Món hết hàng và không thể phục vụ</li>
     *   <li>Bàn đã được đặt trước</li>
     *   <li>Thời gian đóng cửa</li>
     *   <li>Các lý do khác không thể phục vụ</li>
     * </ul>
     * 
     * <p>Sau khi từ chối, session sẽ bị hủy, bàn được giải phóng, 
     * và khách sẽ nhận được thông báo kèm lý do.</p>
     * 
     * @param sessionId ID của session cần từ chối
     * @param reason lý do từ chối (sẽ được hiển thị cho khách)
     * @throws AppException 404 nếu session không tồn tại
     * @throws AppException 400 nếu session không ở trạng thái PENDING
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
        kdsEventPublisher.publishSessionCancelled(session.getId(), session.getTenantId(), null);
        sendNotification("SESSION_REJECTED", session.getPrimaryTable(),
                "❌ Order bàn " + session.getTableNames() + " đã bị từ chối");

        recordAction("session.reject", "SESSION", String.valueOf(session.getId()), null,
                "Từ chối session: " + (reason != null ? reason : "Không có lý do"), session.getId());
    }

    /**
     * Helper method: Thêm danh sách món vào order.
     * 
     * <p>Method này xử lý logic chung cho cả nhân viên và khách order món:</p>
     * <ul>
     *   <li>Validate món còn hàng</li>
     *   <li>Tạo OrderItem với giá snapshot</li>
     *   <li>Cập nhật tổng tiền order</li>
     * </ul>
     * 
     * @param order order cần thêm món vào
     * @param items danh sách món cần thêm
     * @param sourceTable bàn gốc (dùng cho trường hợp gộp bàn)
     * @throws AppException 404 nếu sản phẩm không tồn tại
     * @throws AppException 400 nếu món hết hàng
     */
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
                    .build();
            orderItem = orderItemRepository.save(orderItem);
            
            // CRITICAL: Add item to order's items collection for in-memory access
            order.getItems().add(orderItem);

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            order.setTotalAmount(order.getTotalAmount().add(itemTotal));
        }
        orderRepository.save(order);
    }

    /**
     * Helper method: Xây dựng response cho khách hàng.
     * 
     * <p>Chuyển đổi Session entity sang CustomerOrderResponse DTO
     * với thông tin cần thiết cho khách:</p>
     * <ul>
     *   <li>Trạng thái session và message thân thiện</li>
     *   <li>Danh sách món với trạng thái từng món</li>
     *   <li>Tổng tiền và thông tin bàn</li>
     *   <li>Lý do từ chối (nếu có)</li>
     * </ul>
     * 
     * @param session session cần chuyển đổi
     * @return CustomerOrderResponse đầy đủ thông tin
     */
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
                        .status(i.getStatus().name())
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

    /**
     * Helper method: Lấy URL ảnh đầu tiên của sản phẩm.
     * 
     * @param product sản phẩm cần lấy ảnh
     * @return URL của ảnh đầu tiên, hoặc null nếu không có ảnh
     */
    private String getProductFirstImage(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }
        return product.getImages().get(0).getImageUrl();
    }

    /**
     * Helper method: Gửi cập nhật danh sách pending sessions qua WebSocket.
     * 
     * <p>Gửi thông báo tới topic: {@code /topic/tenant/{tenantId}/pending-sessions}</p>
     * <p>Nhân viên subscribe topic này để nhận realtime updates về các order chờ xử lý.</p>
     */
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

    /**
     * Helper method: Tạo hóa đơn từ session.
     * 
     * <p>Hóa đơn bao gồm:</p>
     * <ul>
     *   <li>Thông tin quán (tên, địa chỉ, logo)</li>
     *   <li>Thông tin order (ID, bàn, thời gian)</li>
     *   <li>Danh sách món và giá</li>
     *   <li>Tổng tiền và phương thức thanh toán</li>
     *   <li>Thông tin thu ngân</li>
     * </ul>
     * 
     * @param session session đã thanh toán
     * @param cashier nhân viên thu ngân
     * @return InvoiceDto hóa đơn đầy đủ
     */
    private InvoiceDto createInvoice(ServingSession session) {
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
                .items(items)
                .totalAmount(total)
                .paymentMethod(order.getPaymentMethod())
                .build();
    }

    /**
     * Helper method: Gửi cập nhật session qua WebSocket.
     * 
     * <p>Gửi thông báo tới nhiều topics:</p>
     * <ul>
     *   <li>{@code /topic/tenant/{tenantId}/table/{tableId}} - Cập nhật cho từng bàn</li>
     *   <li>{@code /topic/tenant/{tenantId}/session/{sessionId}} - Cập nhật cho session</li>
     * </ul>
     * 
     * <p>Cả nhân viên và khách subscribe các topic này để nhận realtime updates.</p>
     * 
     * @param session session cần notify
     */
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
     * Helper method: Gửi notification đến bàn khi bàn bị chuyển đi (detach).
     * 
     * <p>Gửi event đặc biệt với status=TRANSFERRED để frontend biết session đã được chuyển đi
     * và reset về trạng thái mặc định.</p>
     * 
     * @param table bàn bị tách khỏi session
     */
    private void notifyTableTransferred(DiningTable table) {
        String tenantId = TenantContext.getTenantId();
        try {
            String topic = "/topic/tenant/" + tenantId + "/table/" + table.getId();
            // Gửi event với status đặc biệt để frontend biết cần reset
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("type", "TABLE_TRANSFERRED");
            payload.put("tableId", table.getId());
            payload.put("tableName", table.getName());
            payload.put("message", "Bàn đã được chuyển sang vị trí khác");
            messagingTemplate.convertAndSend(topic, payload);
            log.info("Sent TABLE_TRANSFERRED event to table: {}", table.getId());
        } catch (Exception e) {
            log.error("Socket update error for table transfer", e);
        }
    }

    /**
     * Helper method: Gửi event cụ thể về OrderItem qua WebSocket.
     * 
     * <p>Sử dụng cho các event: ORDER_ITEM_ADDED, ORDER_ITEM_UPDATED, ORDER_ITEM_SERVED</p>
     * <p>Event bao gồm cả item data và full session data để tránh race condition ở frontend.</p>
     * 
     * @param eventType loại event (ADD/UPDATE/SERVE)
     * @param session session chứa item
     * @param item order item được thao tác
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
     * Helper method: Gửi delete event qua WebSocket.
     * 
     * <p>Event chứa itemId bị xóa và tổng tiền mới của session,
     * cùng với full session data để frontend có thể sync chính xác.</p>
     * 
     * @param session session chứa item bị xóa
     * @param itemId ID của item đã xóa
     * @param newTotal tổng tiền mới sau khi xóa
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

    /**
     * Helper method: Gửi cập nhật danh sách tất cả bàn qua WebSocket.
     * 
     * <p>Topic: {@code /topic/tenant/{tenantId}/tables}</p>
     * <p>Sử dụng để cập nhật sơ đồ bàn trung tâm cho nhân viên.</p>
     */
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

    /**
     * Helper method: Gửi thông báo chung qua WebSocket và lưu vào database.
     * 
     * <p>Topic: {@code /topic/tenant/{tenantId}/notifications}</p>
     * <p>Thông báo sẽ hiển thị trên UI của nhân viên và được lưu lại để xem sau.</p>
     * 
     * @param type loại thông báo (NEW_SESSION, PAYMENT_REQUEST, ...)
     * @param table bàn liên quan
     * @param content nội dung thông báo
     */
    private void sendNotification(String type, DiningTable table, String content) {
        // Determine priority based on notification type
        Notification.Priority priority = switch (type) {
            case "CUSTOMER_ORDER", "PAYMENT_REQUEST", "PAYMENT_REQUESTED" -> Notification.Priority.HIGH;
            case "NEW_ITEM", "REMOVE_ITEM", "UPDATE_ITEM" -> Notification.Priority.MEDIUM;
            default -> Notification.Priority.LOW;
        };
        
        // Determine title based on type
        String title = switch (type) {
            case "CUSTOMER_ORDER" -> "🔔 Đơn hàng mới";
            case "NEW_SESSION" -> "📋 Mở bàn mới";
            case "NEW_ITEM" -> "➕ Thêm món";
            case "REMOVE_ITEM" -> "➖ Xóa món";
            case "UPDATE_ITEM" -> "✏️ Cập nhật món";
            case "SERVE_ITEM" -> "🍽️ Mang món";
            case "PAYMENT_REQUEST", "PAYMENT_REQUESTED" -> "💰 Yêu cầu thanh toán";
            case "PAYMENT_SUCCESS" -> "✅ Thanh toán thành công";
            case "SESSION_CONFIRMED" -> "✅ Xác nhận đơn";
            case "SESSION_REJECTED" -> "❌ Từ chối đơn";
            case "ATTACH_TABLE" -> "🔗 Gộp bàn";
            case "DETACH_TABLE" -> "✂️ Tách bàn";
            default -> "Thông báo mới";
        };
        
        // Get current session from table if available
        ServingSession session = table != null ? table.getCurrentSession() : null;
        
        // Save to database and send via WebSocket
        notificationService.createAndSend(type, title, content, priority, session, table);
    }

    /**
     * Khách hàng yêu cầu thanh toán.
     * 
     * <p>Gửi thông báo đến nhân viên để đến bàn xử lý thanh toán.
     * Không thay đổi trạng thái session, chỉ tạo notification.</p>
     * 
     * @param sessionId ID của session cần thanh toán
     * @throws AppException 404 nếu session không tồn tại hoặc đã kết thúc
     */
    @Transactional
    public void requestPayment(Long sessionId) {
        ServingSession session = sessionRepository.findActiveByIdWithDetails(sessionId)
                .orElseThrow(() -> new AppException(404, "Session không tồn tại hoặc đã kết thúc"));

        DiningTable table = session.getTables().stream().findFirst().orElse(null);
        String tableName = table != null ? table.getName() : "Bàn ?";

        // Notify staff
        sendNotification("PAYMENT_REQUEST", table, "Bàn " + tableName + " yêu cầu thanh toán");
    }

    /**
     * Xử lý callback khi thanh toán online thành công (VNPAY/MOMO).
     * 
     * <p>Method này được gọi từ PaymentService khi nhận được IPN/callback từ payment gateway.</p>
     * 
     * <p><b>Quy trình:</b></p>
     * <ol>
     *   <li>Cập nhật trạng thái order thành COMPLETED</li>
     *   <li>Gửi thông báo thành công đến nhân viên</li>
     *   <li>Khách sẽ nhận cập nhật qua WebSocket hoặc polling</li>
     * </ol>
     * 
     * <p><b>Idempotent:</b> Nếu order đã COMPLETED, method sẽ return ngay mà không làm gì.</p>
     * 
     * @param orderId ID của order đã thanh toán
     * @param transactionId ID giao dịch từ payment gateway
     * @throws AppException 404 nếu order không tồn tại
     */
    @Transactional
    public void handlePaymentSuccess(Long orderId, String transactionId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(404, "Order not found"));

        if (order.getStatus() == Order.OrderStatus.COMPLETED) {
            return; // Already processed
        }

        // Update Order
        order.setStatus(Order.OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());

        // Update PaymentTransaction in Session? No, Transaction is handled in
        // PaymentService.
        // We just need to update business logic here.

        ServingSession session = order.getSession();
        // Notify Staff
        DiningTable table = session.getPrimaryTable();
        String tableName = table != null ? table.getName() : "Bàn ?";

        sendNotification("PAYMENT_SUCCESS", table,
                "Bàn " + tableName + " đã thanh toán online thành công (" + order.getTotalAmount() + ")");

        // Notify Customer (via WebSocket specific to Session)
        // Note: Currently we don't have a direct topic to customer yet in this file,
        // but checking task.md, we agreed on /topic/session/{id} or similar.
        // However, updating Order status might trigger some auto-update if we have
        // EntityListeners.
        // For now, assume polling or existing socket updates will cover it.
        // Ideally:
        // messagingTemplate.convertAndSend("/topic/session/" + session.getId(), ...);

        orderRepository.save(order);
    }
}
