package com.project.fnb.modules.pos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.fnb.modules.pos.dto.NotificationResponse;
import com.project.fnb.modules.pos.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new com.project.fnb.common.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void getNotifications_ShouldReturnPage() throws Exception {
        when(notificationService.getNotifications(any(Pageable.class))).thenReturn(null);
        
        mockMvc.perform(get("/api/notifications"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).getNotifications(any(Pageable.class));
    }

    @Test
    void getUnreadNotifications_ShouldReturnList() throws Exception {
        when(notificationService.getUnreadNotifications()).thenReturn(Collections.emptyList());
        
        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).getUnreadNotifications();
    }

    @Test
    void countUnread_ShouldReturnLong() throws Exception {
        when(notificationService.countUnread()).thenReturn(5L);
        
        mockMvc.perform(get("/api/notifications/unread/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(5));
        verify(notificationService).countUnread();
    }

    @Test
    void getRecentNotifications_ShouldReturnList() throws Exception {
        when(notificationService.getRecentNotifications(10)).thenReturn(Collections.emptyList());
        
        mockMvc.perform(get("/api/notifications/recent").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).getRecentNotifications(10);
    }

    @Test
    void markAsRead_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).markAsRead(1L);
    }

    @Test
    void markAllAsRead_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).markAllAsRead();
    }
}
