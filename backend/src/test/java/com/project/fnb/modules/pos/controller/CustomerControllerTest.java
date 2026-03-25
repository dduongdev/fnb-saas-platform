package com.project.fnb.modules.pos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.fnb.common.exception.GlobalExceptionHandler;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.menu.service.MenuService;
import com.project.fnb.modules.pos.dto.CustomerOrderRequest;
import com.project.fnb.modules.pos.dto.CustomerOrderResponse;
import com.project.fnb.modules.pos.service.OrderService;
import com.project.fnb.modules.pos.service.SessionService;
import com.project.fnb.modules.pos.service.TableService;
import com.project.fnb.modules.pos.repository.TableRepository;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.global.entity.Tenant;
import java.util.Optional;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class CustomerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SessionService sessionService;

    @Mock
    private MenuService menuService;

    @Mock
    private OrderService orderService;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private CustomerController customerController;

    private ObjectMapper objectMapper;
    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(customerController)
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
    void createCustomerOrder_ShouldReturnResponse() throws Exception {
        CustomerOrderRequest request = new CustomerOrderRequest();
        request.setTableId("t1");
        com.project.fnb.modules.pos.dto.AddItemRequest item = new com.project.fnb.modules.pos.dto.AddItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);
        request.setItems(java.util.List.of(item));
        request.setTableId("t1");
        com.project.fnb.modules.pos.dto.AddItemRequest item1 = new com.project.fnb.modules.pos.dto.AddItemRequest();
        item1.setProductId(1L);
        item1.setQuantity(1);
        request.setItems(java.util.List.of(item1));

        CustomerOrderResponse response = CustomerOrderResponse.builder().build();
        response.setSessionId(1L);

        when(sessionService.createCustomerOrder(any(CustomerOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/pos/public/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(1));

        verify(sessionService).createCustomerOrder(any(CustomerOrderRequest.class));
    }

    @Test
    void getCustomerOrderStatus_ShouldReturnStatus() throws Exception {
        CustomerOrderResponse response = CustomerOrderResponse.builder().build();
        response.setSessionId(1L);

        when(sessionService.getCustomerOrderStatus(1L)).thenReturn(response);

        mockMvc.perform(get("/api/pos/public/sessions/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(1));

        verify(sessionService).getCustomerOrderStatus(1L);
    }

    @Test
    void requestPayment_ShouldReturnSuccess() throws Exception {
        doNothing().when(sessionService).requestPayment(1L);

        mockMvc.perform(post("/api/pos/public/sessions/1/request-payment")
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(sessionService).requestPayment(1L);
    }

    
    @Test
    void getMenu_ShouldReturnList() throws Exception {
        when(menuService.getPublicMenu()).thenReturn(new java.util.ArrayList<>());
        
        mockMvc.perform(get("/api/pos/public/menu"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(menuService).getPublicMenu();
    }

    @Test
    void getMenu_WithTenantId_ShouldReturnList() throws Exception {
        when(menuService.getPublicMenu()).thenReturn(new java.util.ArrayList<>());

        mockMvc.perform(get("/api/pos/public/menu").param("tenantId", TENANT_ID))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).getPublicMenu();
    }

    @Test
    void getTableInfo_ShouldReturnMap() throws Exception {
        DiningTable table = new DiningTable();
        table.setId("t1");
        table.setTenantId("tenant1");
        when(tableRepository.findById("t1")).thenReturn(Optional.of(table));
        
        Tenant tenant = new Tenant();
        tenant.setId("tenant1");
        tenant.setName("KFC");
        when(tenantRepository.findById("tenant1")).thenReturn(Optional.of(tenant));
        
        mockMvc.perform(get("/api/pos/public/info/t1"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void addItemsToActiveSession_ShouldReturnResponse() throws Exception {
        CustomerOrderResponse response = CustomerOrderResponse.builder().build();
        when(sessionService.addCustomerItems(eq(1L), any())).thenReturn(response);
        
        CustomerOrderRequest request = new CustomerOrderRequest();
        request.setTableId("t1");
        com.project.fnb.modules.pos.dto.AddItemRequest item = new com.project.fnb.modules.pos.dto.AddItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);
        request.setItems(java.util.List.of(item));
        
        mockMvc.perform(post("/api/pos/public/sessions/1/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).addCustomerItems(eq(1L), any());
    }

    @Test
    void removeCustomerItem_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/pos/public/sessions/1/items/100"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(sessionService).removeItem(1L, 100L);
    }
}