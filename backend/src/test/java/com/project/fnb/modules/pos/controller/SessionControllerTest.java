package com.project.fnb.modules.pos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.fnb.common.exception.GlobalExceptionHandler;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.dto.SessionRequest;
import com.project.fnb.modules.pos.dto.SessionResponse;
import com.project.fnb.modules.pos.entity.ServingSession;
import com.project.fnb.modules.pos.service.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class SessionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private SessionController sessionController;

    private ObjectMapper objectMapper;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(sessionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void openSession_ShouldReturnSessionResponse() throws Exception {
        SessionRequest.OpenSession request = new SessionRequest.OpenSession();
        request.setTableId("table-1");
        request.setGuestCount(2);

        ServingSession session = new ServingSession();
        session.setId(1L);

        when(sessionService.openTable(any(SessionRequest.OpenSession.class))).thenReturn(session);

        mockMvc.perform(post("/api/pos/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sessionService).openTable(any(SessionRequest.OpenSession.class));
    }

    @Test
    void getSession_ShouldReturnSession() throws Exception {
        ServingSession session = new ServingSession();
        session.setId(1L);

        when(sessionService.getSession(1L)).thenReturn(session);

        mockMvc.perform(get("/api/pos/sessions/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sessionService).getSession(1L);
    }

    @Test
    void addItems_ShouldReturnSuccess() throws Exception {
        SessionRequest.AddItems request = new SessionRequest.AddItems();
        request.setItems(new ArrayList<>());

        doNothing().when(sessionService).addItems(eq(1L), any(SessionRequest.AddItems.class));

        mockMvc.perform(post("/api/pos/sessions/1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
                

        verify(sessionService).addItems(eq(1L), any(SessionRequest.AddItems.class));
    }

    @Test
    void attachTable_ShouldReturnSuccess() throws Exception {
        SessionRequest.AttachTable request = new SessionRequest.AttachTable();
        request.setTableId("table-2");

        doNothing().when(sessionService).attachTable(1L, "table-2");

        mockMvc.perform(post("/api/pos/sessions/1/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
                

        verify(sessionService).attachTable(1L, "table-2");
    }

    @Test
    void cancelSession_ShouldReturnSuccess() throws Exception {
        SessionRequest.CancelSession request = new SessionRequest.CancelSession();
        request.setReason("Test Reason");

        doNothing().when(sessionService).cancelSession(eq(1L), any(SessionRequest.CancelSession.class));

        mockMvc.perform(post("/api/pos/sessions/1/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
                

        verify(sessionService).cancelSession(eq(1L), any(SessionRequest.CancelSession.class));
    }

    
    @Test
    void getPendingSessions_ShouldReturnList() throws Exception {
        when(sessionService.getPendingSessions()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/api/pos/sessions/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).getPendingSessions();
    }

    @Test
    void getActiveSessions_ShouldReturnList() throws Exception {
        when(sessionService.getActiveSessions()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/api/pos/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).getActiveSessions();
    }

    @Test
    void confirmSession_ShouldReturnSession() throws Exception {
        ServingSession session = new ServingSession();
        when(sessionService.confirmSession(eq(1L))).thenReturn(session);
        mockMvc.perform(post("/api/pos/sessions/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).confirmSession(1L);
    }

    @Test
    void rejectSession_ShouldReturnSuccess() throws Exception {
        SessionRequest.CancelSession request = new SessionRequest.CancelSession();
        request.setReason("Guest no show");
        
        mockMvc.perform(post("/api/pos/sessions/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).rejectSession(eq(1L), any());
    }

    @Test
    void removeItem_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/pos/sessions/1/items/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).removeItem(1L, 100L);
    }

    @Test
    void serveItem_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/pos/sessions/1/items/100/serve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).serveItem(1L, 100L);
    }

    @Test
    void updateItem_ShouldReturnSuccess() throws Exception {
        SessionRequest.UpdateItem request = new SessionRequest.UpdateItem();
        request.setQuantity(5);
        
        mockMvc.perform(patch("/api/pos/sessions/1/items/100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).updateItemQuantity(1L, 100L, 5);
    }

    @Test
    void detachTable_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/pos/sessions/1/tables/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).detachTable(1L, "t1");
    }

    @Test
    void paySession_ShouldReturnInvoice() throws Exception {
        com.project.fnb.modules.pos.dto.InvoiceDto invoice = com.project.fnb.modules.pos.dto.InvoiceDto.builder()
            .orderId(10L).totalAmount(java.math.BigDecimal.valueOf(100000)).build();
        when(sessionService.paySession(eq(1L), any())).thenReturn(invoice);
        
        SessionRequest.PaySession request = new SessionRequest.PaySession();
        request.setMethod("CASH");
        
        mockMvc.perform(post("/api/pos/sessions/1/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).paySession(eq(1L), any());
    }
}