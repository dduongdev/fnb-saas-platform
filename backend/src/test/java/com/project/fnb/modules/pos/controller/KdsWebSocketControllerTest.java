package com.project.fnb.modules.pos.controller;

import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.dto.KdsEventType;
import com.project.fnb.modules.pos.dto.KdsSessionDto;
import com.project.fnb.modules.pos.dto.KdsUpdatePayload;
import com.project.fnb.modules.pos.service.KdsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KdsWebSocketControllerTest {

    @Mock
    private KdsService kdsService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private KdsWebSocketController controller;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void handleSubscribe_ShouldSendInitialPayload_ToTenantKitchen() {
        KdsSessionDto session = KdsSessionDto.builder().sessionId(1L).tableNames("Bàn 1").build();
        when(kdsService.getAllActiveSessions()).thenReturn(List.of(session));

        controller.handleSubscribe("tenant-1");

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), payloadCaptor.capture());

        assertEquals("/topic/kds/tenant-1", destinationCaptor.getValue());
        assertInstanceOf(KdsUpdatePayload.class, payloadCaptor.getValue());

        KdsUpdatePayload payload = (KdsUpdatePayload) payloadCaptor.getValue();
        assertEquals(KdsEventType.SESSION_CREATED, payload.getEventType());
        assertEquals(1, ((List<?>) payload.getData()).size());
        assertNotNull(payload.getTimestamp());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void handleSubscribe_ShouldClearTenantContext_WhenServiceThrows() {
        when(kdsService.getAllActiveSessions()).thenThrow(new RuntimeException("ws-fail"));

        controller.handleSubscribe("tenant-1");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(KdsUpdatePayload.class));
        assertNull(TenantContext.getTenantId());
    }
}
