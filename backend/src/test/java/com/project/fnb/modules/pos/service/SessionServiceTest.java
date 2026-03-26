package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.pos.dto.SessionRequest;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.pos.repository.OrderItemRepository;
import com.project.fnb.modules.menu.repository.ProductRepository;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.menu.entity.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.Notification;
import com.project.fnb.modules.pos.repository.SessionRepository;
import com.project.fnb.modules.pos.repository.TableRepository;
import com.project.fnb.modules.pos.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PosActionAuditService posActionAuditService;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SessionService sessionService;

    private String tableId;
    private DiningTable table;

    @BeforeEach
    void setUp() {
        tableId = UUID.randomUUID().toString();
        
        table = new DiningTable();
        table.setId(tableId);
        table.setName("Table 1");
        table.setStatus(DiningTable.Status.AVAILABLE);
    }

    @Test
    void openTable_Available_ShouldCreateSession_AndOrder() {
        // Arrange
        SessionRequest.OpenSession request = new SessionRequest.OpenSession();
        request.setTableId(tableId);
        request.setGuestCount(4);
        
        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        
        ServingSession newSession = new ServingSession();
        newSession.setId(1L);
        newSession.getTables().add(table);
        newSession.setGuestCount(4);
        newSession.setStatus(ServingSession.SessionStatus.ACTIVE);
        when(sessionRepository.save(any(ServingSession.class))).thenReturn(newSession);

        Order newOrder = new Order();
        newOrder.setId(1L);
        when(orderRepository.save(any(Order.class))).thenReturn(newOrder);

        // Act
        ServingSession response = sessionService.openTable(request);

        // Assert
        assertNotNull(response);
        assertEquals(ServingSession.SessionStatus.ACTIVE, response.getStatus());
        assertEquals(DiningTable.Status.OCCUPIED, table.getStatus());
        verify(tableRepository).save(table);
        verify(sessionRepository).save(any(ServingSession.class));
        verify(orderRepository).save(any(Order.class));
        verify(notificationService).createAndSend(anyString(), anyString(), anyString(), any(Notification.Priority.class), any(ServingSession.class), any(DiningTable.class));
    }

    @Test
    void openTable_AlreadyHasSession_ShouldReturnExisting() {
        // Arrange
        SessionRequest.OpenSession request = new SessionRequest.OpenSession();
        request.setTableId(tableId);
        
        ServingSession existingSession = new ServingSession();
        existingSession.setId(2L);
        table.setCurrentSession(existingSession);
        
        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
        lenient().when(sessionRepository.findByIdWithDetails(2L)).thenReturn(Optional.of(existingSession));

        // Act
        ServingSession response = sessionService.openTable(request);

        // Assert
        assertEquals(2L, response.getId());
        verify(sessionRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getSession_Found_ShouldReturnSession() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        lenient().when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));

        ServingSession result = sessionService.getSession(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getSession_NotFound_ShouldThrowException() {
        when(sessionRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> sessionService.getSession(99L));
    }

    @Test
    void removeItem_WhenCompleted_ShouldThrowException() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.COMPLETED);
        
        lenient().when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));

        assertThrows(AppException.class, () -> sessionService.removeItem(1L, 100L));
    }

    @Test
    void attachTable_AvailableTable_ShouldSuccess() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        
        DiningTable newTable = new DiningTable();
        newTable.setId("table-2");
        newTable.setStatus(DiningTable.Status.AVAILABLE);
        
        lenient().when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        when(tableRepository.findById("table-2")).thenReturn(Optional.of(newTable));
        
        sessionService.attachTable(1L, "table-2");
        
        verify(tableRepository).save(newTable);
        verify(sessionRepository).save(session);
        assertEquals(DiningTable.Status.OCCUPIED, newTable.getStatus());
        assertTrue(session.getTables().contains(newTable));
    }

    @Test
    void detachTable_MainTable_ShouldThrowException() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        
        DiningTable t1 = new DiningTable();
        t1.setId("t1");
        session.getTables().add(t1); // Only 1 table
        
        lenient().when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        
        assertThrows(AppException.class, () -> sessionService.detachTable(1L, "t1"));
    }

    
        
    @Test
    void serveItem_ValidItem_ShouldChangeStatusToServed() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        Order order = new Order();
        order.setId(10L);
        session.getOrders().add(order);
        
        Product product = new Product();
        product.setId(200L);

        OrderItem item = new OrderItem();
        item.setId(100L);
        item.setOrder(order);
        item.setProduct(product);
        item.setStatus(OrderItem.ItemStatus.PENDING);
        item.setQuantity(1);
        item.setPrice(BigDecimal.valueOf(50000));
        
        lenient().when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        lenient().when(orderItemRepository.findById(100L)).thenReturn(Optional.of(item));
        
        sessionService.serveItem(1L, 100L);
        
        assertEquals(OrderItem.ItemStatus.SERVED, item.getStatus());
        verify(orderItemRepository).save(item);
    }

    @Test
    void updateItemQuantity_Valid_ShouldUpdateQuantity() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        Order order = new Order();
        order.setId(10L);
        session.getOrders().add(order);
        
        Product product = new Product();
        product.setId(200L);

        OrderItem item = new OrderItem();
        item.setId(100L);
        item.setOrder(order);
        item.setProduct(product);
        item.setStatus(OrderItem.ItemStatus.PENDING);
        item.setQuantity(1);
        item.setPrice(BigDecimal.valueOf(50000));
        
        lenient().when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        lenient().when(orderItemRepository.findById(100L)).thenReturn(Optional.of(item));
        
        sessionService.updateItemQuantity(1L, 100L, 3);
        
        assertEquals(3, item.getQuantity());
        verify(orderItemRepository).save(item);
        verify(posActionAuditService).record("session.update_item_quantity", "ORDER_ITEM", "100", order.getTotalAmount(), "Cập nhật số lượng " + product.getName() + " từ 1 -> 3");
    }

    @Test
    void paySession_ValidSession_ShouldCompleteOrdersAndReleaseTables() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStartedAt(java.time.LocalDateTime.now());
        
        Order order = new Order();
        order.setId(10L);
        order.setStatus(Order.OrderStatus.OPEN);
        session.getOrders().add(order);
        
        DiningTable t1 = new DiningTable();
        t1.setId("t1");
        t1.setCurrentSession(session);
        t1.setStatus(DiningTable.Status.OCCUPIED);
        session.getTables().add(t1);
        
        com.project.fnb.modules.global.entity.Tenant tenant = new com.project.fnb.modules.global.entity.Tenant();
        tenant.setId("foo");
        tenant.setName("name");
        
        lenient().when(tenantRepository.findById(any())).thenReturn(Optional.of(tenant));
        lenient().when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        
        SessionRequest.PaySession request = new SessionRequest.PaySession();
        request.setMethod("CASH");
        
        var response = sessionService.paySession(1L, request);
        
        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        assertNull(t1.getCurrentSession());
        assertEquals(DiningTable.Status.AVAILABLE, t1.getStatus());
        assertEquals(ServingSession.SessionStatus.COMPLETED, session.getStatus());
        
        verify(orderRepository).saveAll(any());
        verify(tableRepository).saveAll(any());
        verify(sessionRepository).save(session);
    }

    
    @Test
    void cancelSession_ValidSession_ShouldCancelAllOrdersAndReleaseTables() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        
        Order order = new Order();
        order.setId(10L);
        order.setStatus(Order.OrderStatus.OPEN);
        session.getOrders().add(order);
        
        DiningTable t1 = new DiningTable();
        t1.setId("t1");
        t1.setCurrentSession(session);
        t1.setStatus(DiningTable.Status.OCCUPIED);
        session.getTables().add(t1);
        
        lenient().when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        
        SessionRequest.CancelSession request = new SessionRequest.CancelSession();
        request.setReason("Customer left");
        
        sessionService.cancelSession(1L, request);
        
        assertEquals(Order.OrderStatus.CANCELLED, order.getStatus());
        
        assertNull(t1.getCurrentSession());
        assertEquals(DiningTable.Status.AVAILABLE, t1.getStatus());
        
        assertEquals(ServingSession.SessionStatus.CANCELLED, session.getStatus());
        assertTrue(session.getNote().contains("Customer left"));
        
        verify(orderRepository).saveAll(any());
        verify(tableRepository).saveAll(any());
        verify(sessionRepository).save(session);
    }


    @Test
    void addItems_Valid_ShouldAddItemsAndNotify() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        Order order = new Order();
        order.setTotalAmount(BigDecimal.ZERO);
        order.setItems(new java.util.HashSet<>());
        session.setOrders(new java.util.HashSet<>(java.util.Collections.singleton(order)));

        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("T1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));

        Product product = new Product();
        product.setId(2L);
        product.setName("Coke");
        product.setPrice(BigDecimal.valueOf(10));
        product.setStatus(Product.ProductStatus.AVAILABLE);
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));

        com.project.fnb.modules.pos.dto.AddItemRequest itemReq = new com.project.fnb.modules.pos.dto.AddItemRequest();
        itemReq.setProductId(2L);
        itemReq.setQuantity(2);

        SessionRequest.AddItems request = new SessionRequest.AddItems();
        request.setSourceTableId("t1");
        request.setItems(java.util.List.of(itemReq));

        when(tableRepository.findById("t1")).thenReturn(Optional.of(table));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(i -> i.getArgument(0));

        sessionService.addItems(1L, request);

        verify(orderRepository).save(order);
        assertEquals(BigDecimal.valueOf(20), order.getTotalAmount());
        verify(notificationService, org.mockito.Mockito.atLeastOnce()).createAndSend(any(), any(), any(), any(com.project.fnb.modules.pos.entity.Notification.Priority.class), any(), any());
    }

    @Test
    void createCustomerOrder_NewSession_ShouldReturnPendingSession() {
        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setStatus(DiningTable.Status.AVAILABLE);

        when(tableRepository.findById("t1")).thenReturn(Optional.of(table));

        com.project.fnb.modules.pos.dto.CustomerOrderRequest req = new com.project.fnb.modules.pos.dto.CustomerOrderRequest();
        req.setTableId("t1");
        req.setCustomerNote("Test note");
        
        com.project.fnb.modules.pos.dto.AddItemRequest item = new com.project.fnb.modules.pos.dto.AddItemRequest();
        item.setProductId(1L);
        item.setQuantity(1);
        req.setItems(java.util.List.of(item));

        when(sessionRepository.save(any(ServingSession.class))).thenAnswer(i -> {
            ServingSession s = i.getArgument(0);
            s.setId(10L);
            return s;
        });

        Order mockOrder = new Order();
        mockOrder.setId(100L);
        mockOrder.setTotalAmount(BigDecimal.ZERO);
        mockOrder.setItems(new java.util.HashSet<>());
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        Product product = new Product();
        product.setId(1L);
        product.setPrice(BigDecimal.valueOf(100));
        product.setStatus(Product.ProductStatus.AVAILABLE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        com.project.fnb.modules.pos.dto.CustomerOrderResponse res = sessionService.createCustomerOrder(req);

        assertNotNull(res);
        verify(tableRepository).save(table);
        assertEquals(10L, res.getSessionId());
        assertEquals(ServingSession.SessionStatus.PENDING, res.getStatus());
    }

    @Test
    void confirmSession_Valid_ShouldChangeStatusAndOccupiedTable() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.PENDING);

        DiningTable table = new DiningTable();
        table.setId("t1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));

        ServingSession result = sessionService.confirmSession(1L);

        assertEquals(ServingSession.SessionStatus.ACTIVE, result.getStatus());
        assertEquals(DiningTable.Status.OCCUPIED, table.getStatus());
        verify(tableRepository, org.mockito.Mockito.atLeastOnce()).save(any());
        verify(sessionRepository).save(session);
    }

    @Test
    void rejectSession_Valid_ShouldCancelAndReleaseTable() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.PENDING);

        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setCurrentSession(session);
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));

        sessionService.rejectSession(1L, "No reason");

        assertEquals(ServingSession.SessionStatus.CANCELLED, session.getStatus());
        assertNull(table.getCurrentSession());
        assertEquals(DiningTable.Status.AVAILABLE, table.getStatus());
        verify(sessionRepository).save(session);
    }

    @Test
    void addCustomerItems_Valid_ShouldReturnResponse() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        
        Order order = new Order();
        order.setTotalAmount(BigDecimal.ZERO);
        order.setItems(new java.util.HashSet<>());
        session.setOrders(new java.util.HashSet<>(java.util.Collections.singleton(order)));

        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("T1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));

        Product product = new Product();
        product.setId(1L);
        product.setPrice(BigDecimal.valueOf(50));
        product.setStatus(Product.ProductStatus.AVAILABLE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        com.project.fnb.modules.pos.dto.CustomerOrderRequest req = new com.project.fnb.modules.pos.dto.CustomerOrderRequest();
        req.setTableId("t1");
        
        com.project.fnb.modules.pos.dto.AddItemRequest item = new com.project.fnb.modules.pos.dto.AddItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);
        req.setItems(java.util.List.of(item));

        com.project.fnb.modules.pos.dto.CustomerOrderResponse res = sessionService.addCustomerItems(1L, req);

        assertNotNull(res);
        verify(orderRepository).save(order);
        assertEquals(BigDecimal.valueOf(100), order.getTotalAmount());
    }

    @Test
    void getCustomerOrderStatus_Valid_ShouldReturnResponse() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        
        Order order = new Order();
        order.setTotalAmount(BigDecimal.ZERO);
        order.setItems(new java.util.HashSet<>());
        session.setOrders(new java.util.HashSet<>(java.util.Collections.singleton(order)));

        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("T1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));

        com.project.fnb.modules.pos.dto.CustomerOrderResponse res = sessionService.getCustomerOrderStatus(1L);
        
        assertNotNull(res);
        assertEquals(1L, res.getSessionId());
        assertEquals(ServingSession.SessionStatus.ACTIVE, res.getStatus());
    }

    @Test
    void getPendingSessions_ShouldReturnList() {
        when(sessionRepository.findPendingSessions()).thenReturn(java.util.Collections.emptyList());
        java.util.List<?> list = sessionService.getPendingSessions();
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    void requestPayment_Valid_ShouldNotify() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);

        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("T1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));

        sessionService.requestPayment(1L);

        verify(notificationService, org.mockito.Mockito.atLeastOnce()).createAndSend(any(), any(), any(), any(), any(), any());
    }


    @Test
    void removeItem_Valid_ShouldDecreaseTotalAndRemove() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        
        Order order = new Order();
        order.setId(100L);
        order.setStatus(Order.OrderStatus.OPEN);
        order.setTotalAmount(BigDecimal.valueOf(100));
        order.setItems(new java.util.HashSet<>());
        session.setOrders(new java.util.HashSet<>(java.util.Collections.singleton(order)));

        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("T1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        OrderItem item = new OrderItem();
        item.setId(10L);
        item.setOrder(order);
        item.setStatus(OrderItem.ItemStatus.PENDING);
        item.setQuantity(2);
        Product prod = new Product();
        prod.setPrice(BigDecimal.valueOf(25));
        item.setProduct(prod);
        item.setPrice(BigDecimal.valueOf(25));
        order.getItems().add(item);

        when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        when(orderItemRepository.findById(10L)).thenReturn(Optional.of(item));

        sessionService.removeItem(1L, 10L);

        verify(orderRepository).save(order);
        verify(orderItemRepository).delete(item);
        assertEquals(BigDecimal.valueOf(50), order.getTotalAmount());
    }

    @Test
    void detachTable_Valid_ShouldRemoveFromSession() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        
        DiningTable table1 = new DiningTable();
        table1.setId("t1");
        table1.setName("T1");
        table1.setCurrentSession(session);
        
        DiningTable table2 = new DiningTable();
        table2.setId("t2");
        table2.setName("T2");
        table2.setCurrentSession(session);
        
        session.setTables(new java.util.HashSet<>(java.util.Arrays.asList(table1, table2)));

        when(sessionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(session));
        when(tableRepository.findById("t2")).thenReturn(Optional.of(table2));

        sessionService.detachTable(1L, "t2");

        assertNull(table2.getCurrentSession());
        assertEquals(DiningTable.Status.AVAILABLE, table2.getStatus());
        assertEquals(1, session.getTables().size());
        verify(tableRepository).save(table2);
        verify(sessionRepository).save(session);
    }

    @Test
    void handlePaymentSuccess_ValidOrder_ShouldMarkCompleted() {
        Order order = new Order();
        order.setId(100L);
        order.setStatus(Order.OrderStatus.OPEN);
        order.setTotalAmount(BigDecimal.valueOf(500));
        
        ServingSession session = new ServingSession();
        DiningTable table = new DiningTable();
        table.setName("T1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));
        order.setSession(session);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        sessionService.handlePaymentSuccess(100L, "txn123");

        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        assertNotNull(order.getCompletedAt());
        verify(orderRepository).save(order);
    }
    
    @Test
    void getActiveSessions_ShouldReturnList() {
        when(sessionRepository.findActiveSessions()).thenReturn(java.util.Collections.emptyList());
        java.util.List<?> result = sessionService.getActiveSessions();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void getOrCreateByTable_ShouldReturnSession() {
        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("T1");
        table.setStatus(DiningTable.Status.AVAILABLE);

        when(tableRepository.findById("t1")).thenReturn(Optional.of(table));
        when(sessionRepository.save(any(ServingSession.class))).thenAnswer(i -> {
            ServingSession s = i.getArgument(0);
            s.setId(1L);
            return s;
        });
        
        Order mockOrder = new Order();
        mockOrder.setTotalAmount(BigDecimal.ZERO);
        mockOrder.setItems(new java.util.HashSet<>());
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        ServingSession result = sessionService.getOrCreateByTable("t1");

        assertNotNull(result);
        assertEquals(ServingSession.SessionStatus.ACTIVE, result.getStatus());
    }

    @Test
    void serveItem_Valid_ShouldChangeStatusToServed() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        
        Order order = new Order();
        order.setId(100L);
        order.setStatus(Order.OrderStatus.OPEN);
        session.setOrders(new java.util.HashSet<>(java.util.Collections.singleton(order)));

        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("T1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        OrderItem item = new OrderItem();
        item.setId(10L);
        item.setOrder(order);
        item.setStatus(OrderItem.ItemStatus.PENDING);
        item.setQuantity(1);
        item.setPrice(java.math.BigDecimal.valueOf(50));
        Product prod = new Product();
        prod.setName("Test Product");
        item.setProduct(prod);

        when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        when(orderItemRepository.findById(10L)).thenReturn(Optional.of(item));

        sessionService.serveItem(1L, 10L);

        assertEquals(OrderItem.ItemStatus.SERVED, item.getStatus());
        verify(orderItemRepository).save(item);
    }

    @Test
    void updateItemQuantity_Valid_ShouldUpdateTotalAndSave() {
        ServingSession session = new ServingSession();
        session.setId(1L);
        session.setStatus(ServingSession.SessionStatus.ACTIVE);
        
        Order order = new Order();
        order.setId(100L);
        order.setStatus(Order.OrderStatus.OPEN);
        order.setTotalAmount(BigDecimal.valueOf(100)); // 2 items * 50
        session.setOrders(new java.util.HashSet<>(java.util.Collections.singleton(order)));

        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("T1");
        session.setTables(new java.util.HashSet<>(java.util.Collections.singleton(table)));

        OrderItem item = new OrderItem();
        item.setId(10L);
        item.setOrder(order);
        item.setStatus(OrderItem.ItemStatus.PENDING);
        item.setQuantity(2);
        item.setPrice(BigDecimal.valueOf(50));
        Product prod = new Product();
        prod.setName("Test Product");
        item.setProduct(prod);

        when(sessionRepository.findActiveByIdWithDetails(1L)).thenReturn(Optional.of(session));
        when(orderItemRepository.findById(10L)).thenReturn(Optional.of(item));

        sessionService.updateItemQuantity(1L, 10L, 3);

        // new item total should be 3 * 50 = 150. Old was 2 * 50 = 100. Diff = +50.
        // New order total = 100 + 50 = 150
        assertEquals(3, item.getQuantity());
        assertEquals(BigDecimal.valueOf(150), order.getTotalAmount());
        verify(orderItemRepository).save(item);
        verify(orderRepository).save(order);
    }
}
