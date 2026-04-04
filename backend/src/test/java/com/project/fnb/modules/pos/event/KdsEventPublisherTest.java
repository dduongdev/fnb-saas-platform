package com.project.fnb.modules.pos.event;

import com.project.fnb.modules.pos.dto.KdsEventType;
import com.project.fnb.modules.pos.dto.KdsOrderItemDto;
import com.project.fnb.modules.pos.dto.KdsSessionDto;
import com.project.fnb.modules.pos.dto.KdsUpdatePayload;
import com.project.fnb.modules.pos.entity.OrderItem;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.pos.service.KdsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KdsEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private KdsService kdsService;

    @InjectMocks
    private KdsEventPublisher eventPublisher;

    @Test
    void publishSessionCreated_ShouldSendSessionSnapshotToCorrectTopic() {
        ServingSession session = new ServingSession();
        session.setId(10L);
        session.setTenantId("tenant-1");

        KdsSessionDto dto = KdsSessionDto.builder().sessionId(10L).tableNames("Bàn 1").build();
        when(kdsService.getAllActiveSessions()).thenReturn(List.of(dto));

        ArgumentCaptor<KdsUpdatePayload> payloadCaptor = ArgumentCaptor.forClass(KdsUpdatePayload.class);

        eventPublisher.publishSessionCreated(session, "kitchenX");

        verify(messagingTemplate).convertAndSend(eq("/topic/kds/tenant-1/kitchenX"), payloadCaptor.capture());
        KdsUpdatePayload payload = payloadCaptor.getValue();

        assertNotNull(payload);
        assertEquals(KdsEventType.SESSION_CREATED, payload.getEventType());
        assertEquals(dto, payload.getData());
        assertEquals(10L, payload.getSessionId());
        assertNotNull(payload.getTimestamp());
    }

    @Test
    void publishSessionCancelled_ShouldSendSessionCancelledEvent() {
        ArgumentCaptor<KdsUpdatePayload> payloadCaptor = ArgumentCaptor.forClass(KdsUpdatePayload.class);

        eventPublisher.publishSessionCancelled(999L, "tenant-2", "kitchenA");

        verify(messagingTemplate).convertAndSend(eq("/topic/kds/tenant-2/kitchenA"), payloadCaptor.capture());
        KdsUpdatePayload payload = payloadCaptor.getValue();

        assertNotNull(payload);
        assertEquals(KdsEventType.SESSION_CANCELLED, payload.getEventType());
        assertEquals(999L, payload.getSessionId());
    }

    @Test
    void publishItemAdded_ShouldSendItemAddedEventAndItemDto() {
        Product product = new Product();
        product.setName("Bún Bò");

        OrderItem item = new OrderItem();
        item.setId(500L);
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("120000"));
        item.setNote("Giảm mỡ");
        item.setStatus(OrderItem.ItemStatus.PENDING);
        item.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(4));
        item.setTenantId("tenant-3");

        ArgumentCaptor<KdsUpdatePayload> payloadCaptor = ArgumentCaptor.forClass(KdsUpdatePayload.class);

        eventPublisher.publishItemAdded(item, 88L, "kitchenB");

        verify(messagingTemplate).convertAndSend(eq("/topic/kds/tenant-3/kitchenB"), payloadCaptor.capture());
        KdsUpdatePayload payload = payloadCaptor.getValue();

        assertNotNull(payload);
        assertEquals(KdsEventType.ITEM_ADDED, payload.getEventType());
        assertEquals(88L, payload.getSessionId());
        assertTrue(payload.getData() instanceof KdsOrderItemDto);

        KdsOrderItemDto dto = (KdsOrderItemDto) payload.getData();
        assertEquals(500L, dto.getItemId());
        assertEquals("Bún Bò", dto.getProductName());
        assertEquals(2, dto.getQuantity());
        assertEquals("Giảm mỡ", dto.getNotes());
        assertEquals(OrderItem.ItemStatus.PENDING.name(), dto.getStatus());
    }
}
