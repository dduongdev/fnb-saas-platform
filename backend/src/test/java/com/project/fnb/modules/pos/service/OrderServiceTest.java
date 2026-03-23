package com.project.fnb.modules.pos.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.repository.OrderItemRepository;
import com.project.fnb.modules.pos.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void removeItem_ValidItem_ShouldRemoveAndDeductTotal() {
        ServingSession session = new ServingSession();
        DiningTable primaryTable = new DiningTable();
        primaryTable.setId("t1");
        session.getTables().add(primaryTable);

        Order order = new Order();
        order.setSession(session);
        order.setTotalAmount(BigDecimal.valueOf(100000));
        order.setItems(new HashSet<>());
        
        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrder(order);
        item.setStatus(OrderItem.ItemStatus.PENDING);
        item.setPrice(BigDecimal.valueOf(25000));
        item.setQuantity(2);
        
        order.getItems().add(item);
        
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(item));
        
        orderService.removeItem(1L);
        
        assertEquals(0, order.getItems().size());
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(order.getTotalAmount()));
        
        verify(orderItemRepository).delete(item);
        verify(orderRepository).save(order);
    }

    @Test
    void removeItem_ItemNotFound_ShouldThrowAppException() {
        when(orderItemRepository.findById(1L)).thenReturn(Optional.empty());
        AppException ex = assertThrows(AppException.class, () -> orderService.removeItem(1L));
        assertEquals(404, ex.getErrorCode());
    }

    @Test
    void removeItem_ItemNotPending_ShouldThrowAppException() {
        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setStatus(OrderItem.ItemStatus.SERVED);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(item));
        
        AppException ex = assertThrows(AppException.class, () -> orderService.removeItem(1L));
        assertEquals(400, ex.getErrorCode());
    }

    @Test
    void notifyPaymentSuccess_ValidOrder_ShouldSendSocketMessage() {
        ServingSession session = new ServingSession();
        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("Bàn 1");
        session.getTables().add(table);

        Order order = new Order();
        order.setId(10L);
        order.setTenantId("tenant1");
        order.setTotalAmount(BigDecimal.valueOf(100000));
        order.setSession(session);
        
        orderService.notifyPaymentSuccess(order);
        
        // notifyOrderUpdate and notifyPaymentSuccess send 2 messages total
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(Object.class));
    }
}
