package com.project.fnb.modules.pos.service;

import com.project.fnb.modules.pos.dto.KdsOrderItemDto;
import com.project.fnb.modules.pos.dto.KdsSessionDto;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.pos.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KdsServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private KdsService kdsService;

    @Test
    void getAllActiveSessions_ShouldReturnSortedSessionsByCreatedAtAndSetPendingCount() {
        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setName("Bàn 1");

        Product rice = new Product();
        rice.setName("Cơm");

        OrderItem itemPending = new OrderItem();
        itemPending.setId(101L);
        itemPending.setProduct(rice);
        itemPending.setQuantity(1);
        itemPending.setStatus(OrderItem.ItemStatus.PENDING);
        itemPending.setCreatedAt(LocalDateTime.now().minusMinutes(15));

        OrderItem itemServed = new OrderItem();
        itemServed.setId(102L);
        itemServed.setProduct(rice);
        itemServed.setQuantity(2);
        itemServed.setStatus(OrderItem.ItemStatus.SERVED);
        itemServed.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        Order order = new Order();
        order.setId(201L);
        order.getItems().add(itemPending);
        order.getItems().add(itemServed);

        ServingSession sessionNew = new ServingSession();
        sessionNew.setId(1L);
        sessionNew.setCreatedAt(LocalDateTime.now().minusMinutes(3));
        sessionNew.getTables().add(table);
        sessionNew.getOrders().add(order);

        ServingSession sessionOld = new ServingSession();
        sessionOld.setId(2L);
        sessionOld.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        sessionOld.getTables().add(table);
        sessionOld.getOrders().add(order);

        when(sessionRepository.findAllActive()).thenReturn(List.of(sessionNew, sessionOld));

        List<KdsSessionDto> result = kdsService.getAllActiveSessions();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getSessionId());
        assertEquals(2L, result.get(1).getSessionId());

        KdsSessionDto firstDto = result.get(0);
        assertEquals("Bàn 1", firstDto.getTableNames());
        assertEquals(2, firstDto.getItems().size());
        assertEquals(1, firstDto.getPendingItemCount());
        assertNotNull(firstDto.getTotalMinutesWaited());

        List<KdsOrderItemDto> orderedItems = firstDto.getItems();
        assertEquals(OrderItem.ItemStatus.PENDING.name(), orderedItems.get(0).getStatus());
        assertEquals(OrderItem.ItemStatus.SERVED.name(), orderedItems.get(1).getStatus());
    }

    @Test
    void getPendingItemsForSession_ShouldOnlyReturnPendingItems() {
        ServingSession session = new ServingSession();
        session.setId(10L);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(40));

        Product phaLo = new Product();
        phaLo.setName("Phá Lấu");

        OrderItem pending = new OrderItem();
        pending.setId(111L);
        pending.setStatus(OrderItem.ItemStatus.PENDING);
        pending.setProduct(phaLo);
        pending.setQuantity(3);
        pending.setCreatedAt(LocalDateTime.now().minusMinutes(20));

        OrderItem served = new OrderItem();
        served.setId(112L);
        served.setStatus(OrderItem.ItemStatus.SERVED);
        served.setProduct(phaLo);
        served.setQuantity(1);
        served.setCreatedAt(LocalDateTime.now().minusMinutes(10));

        Order order = new Order();
        order.getItems().add(pending);
        order.getItems().add(served);
        session.getOrders().add(order);

        when(sessionRepository.findAllActive()).thenReturn(List.of(session));

        List<KdsOrderItemDto> pendingItems = kdsService.getPendingItemsForSession(10L);

        assertEquals(1, pendingItems.size());
        assertEquals(111L, pendingItems.get(0).getItemId());
        assertEquals("Phá Lấu", pendingItems.get(0).getProductName());
        assertEquals(OrderItem.ItemStatus.PENDING.name(), pendingItems.get(0).getStatus());
    }
}
