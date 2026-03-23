package com.project.fnb.modules.pos.service;

import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.dto.NotificationResponse;
import com.project.fnb.modules.pos.entity.Notification;
import com.project.fnb.modules.pos.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.entity.DiningTable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createAndSend_ShouldSaveAndBroadcast() {
        ServingSession session = new ServingSession();
        DiningTable table = new DiningTable();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.createAndSend("ORDER", "New Order", "You have a new order", session, table);

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());

        Notification saved = notifCaptor.getValue();
        assertEquals("ORDER", saved.getType());
        assertEquals("New Order", saved.getTitle());

        verify(messagingTemplate).convertAndSend(eq("/topic/tenant/" + TENANT_ID + "/notifications"), any(NotificationResponse.class));
    }

    @Test
    void createHighPriority_ShouldSaveWithHighPriorityAndBroadcast() {
        ServingSession session = new ServingSession();
        DiningTable table = new DiningTable();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.createHighPriority("URGENT", "Alert", "Urgent message", session, table);

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());

        Notification saved = notifCaptor.getValue();
        assertEquals("URGENT", saved.getType());
        assertEquals("Alert", saved.getTitle());
        assertEquals(Notification.Priority.HIGH, saved.getPriority());
    }

    @Test
    void getNotifications_ShouldReturnPage() {
        Notification notif = new Notification();
        notif.setId(1L);
        notif.setType("INFO");
        notif.setContent("Message");
        notif.setPriority(Notification.Priority.LOW);
        
        Page<Notification> page = new PageImpl<>(List.of(notif));
        when(notificationRepository.findAllByTenantOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);
        
        Page<NotificationResponse> result = notificationService.getNotifications(PageRequest.of(0, 10));
        
        assertEquals(1, result.getTotalElements());
        assertEquals("INFO", result.getContent().get(0).getType());
    }

    @Test
    void getUnreadNotifications_ShouldReturnList() {
        Notification notif1 = new Notification();
        notif1.setId(1L);
        notif1.setTitle("Test");
        notif1.setType("INFO");
        notif1.setPriority(Notification.Priority.MEDIUM);

        when(notificationRepository.findUnreadNotifications())
                .thenReturn(List.of(notif1));

        List<NotificationResponse> results = notificationService.getUnreadNotifications();

        assertEquals(1, results.size());
        assertEquals("Test", results.get(0).getTitle());
    }

    @Test
    void countUnread_ShouldReturnCount() {
        when(notificationRepository.countUnreadNotifications()).thenReturn(5L);
        Long result = notificationService.countUnread();
        assertEquals(5L, result);
    }

    @Test
    void getRecentNotifications_ShouldReturnList() {
        Notification notif1 = new Notification();
        notif1.setId(1L);
        notif1.setTitle("Recent");
        notif1.setType("INFO");
        notif1.setPriority(Notification.Priority.MEDIUM);

        when(notificationRepository.findRecentNotifications(any(Pageable.class))).thenReturn(List.of(notif1));
        
        List<NotificationResponse> results = notificationService.getRecentNotifications(5);
        assertEquals(1, results.size());
        assertEquals("Recent", results.get(0).getTitle());
    }

    @Test
    void markAsRead_ShouldUpdateStatus() {
        notificationService.markAsRead(1L);
        verify(notificationRepository).markAsRead(eq(1L), any());
    }

    @Test
    void markAllAsRead_ShouldUpdateAll() {
        notificationService.markAllAsRead();
        verify(notificationRepository).markAllAsRead(any());
    }
}
